package com.example.rag.service.impl;

import com.example.rag.dto.EvalCasePage;
import com.example.rag.dto.EvalCaseView;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalRunPage;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunSummary;
import com.example.rag.dto.EvalRunView;
import com.example.rag.entity.EvalCaseRecord;
import com.example.rag.entity.EvalRunRecord;
import com.example.rag.repository.EvalCaseRecordRepository;
import com.example.rag.repository.EvalRunRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EvalHistoryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    private final EvalRunRecordRepository runRepository;
    private final EvalCaseRecordRepository caseRepository;
    private final EvalComparisonService comparisonService;
    private final ObjectMapper objectMapper;

    public EvalHistoryService(EvalRunRecordRepository runRepository,
                              EvalCaseRecordRepository caseRepository,
                              EvalComparisonService comparisonService,
                              ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.caseRepository = caseRepository;
        this.comparisonService = comparisonService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long begin(String questionSetSha256, int caseCount) {
        if (questionSetSha256 == null || !questionSetSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("question-set digest must be SHA-256 hex");
        }
        if (caseCount <= 0) {
            throw new IllegalArgumentException("fixed eval must have at least one case");
        }
        EvalRunRecord record = new EvalRunRecord();
        record.setStatus(RUNNING);
        record.setQuestionSetSha256(questionSetSha256);
        record.setStartedAt(LocalDateTime.now());
        record.setCaseCount(caseCount);
        return runRepository.saveAndFlush(record).getId();
    }

    @Transactional
    public void complete(long runId, EvalRunSummary summary) {
        EvalRunRecord record = requireRun(runId);
        if (!RUNNING.equals(record.getStatus())) {
            throw new IllegalStateException("eval run is not running: " + runId);
        }
        validateSummary(runId, record, summary);
        List<EvalCaseRecord> rows = new ArrayList<>();
        int position = 0;
        for (EvalRunResult result : summary.getResults()) {
            EvalCaseRecord row = new EvalCaseRecord();
            row.setRunId(runId);
            row.setPosition(position++);
            row.setCaseId(result.getId());
            row.setResultJson(writeJson(result));
            rows.add(row);
        }
        caseRepository.saveAll(rows);
        caseRepository.flush();
        record.setSummaryJson(writeJson(summary));
        record.setCompletedAt(LocalDateTime.now());
        record.setStatus(COMPLETED);
        runRepository.saveAndFlush(record);
    }

    @Transactional
    public void fail(long runId, RuntimeException failure) {
        EvalRunRecord record = requireRun(runId);
        if (!RUNNING.equals(record.getStatus())) {
            return;
        }
        record.setStatus(FAILED);
        record.setCompletedAt(LocalDateTime.now());
        String type = failure == null ? "UnknownError" : failure.getClass().getSimpleName();
        record.setErrorMessage("Evaluation failed: " + type);
        runRepository.save(record);
    }

    @Transactional(readOnly = true)
    public EvalRunView get(long runId) {
        return toView(requireRun(runId));
    }

    @Transactional(readOnly = true)
    public EvalRunPage list(int limit, Long before) {
        requireLimit(limit);
        if (before != null && before <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be a positive run ID");
        }
        PageRequest page = PageRequest.of(0, limit + 1);
        List<EvalRunRecord> records = before == null
                ? runRepository.findAllByOrderByIdDesc(page)
                : runRepository.findByIdLessThanOrderByIdDesc(before, page);
        boolean hasMore = records.size() > limit;
        List<EvalRunView> items = new ArrayList<>();
        for (int index = 0; index < Math.min(records.size(), limit); index++) {
            items.add(toView(records.get(index)));
        }
        Long nextBefore = hasMore ? items.get(items.size() - 1).runId() : null;
        return new EvalRunPage(List.copyOf(items), nextBefore);
    }

    @Transactional(readOnly = true)
    public EvalCasePage cases(long runId, int limit, Integer after) {
        requireLimit(limit);
        if (after != null && after < -1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "after must be a case position >= -1");
        }
        EvalRunRecord run = requireRun(runId);
        requireComplete(run);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<EvalCaseRecord> records = after == null
                ? caseRepository.findByRunIdOrderByPositionAsc(runId, page)
                : caseRepository.findByRunIdAndPositionGreaterThanOrderByPositionAsc(runId, after, page);
        boolean hasMore = records.size() > limit;
        List<EvalCaseView> items = new ArrayList<>();
        for (int index = 0; index < Math.min(records.size(), limit); index++) {
            items.add(toCaseView(records.get(index)));
        }
        Integer nextAfter = hasMore ? items.get(items.size() - 1).position() : null;
        return new EvalCasePage(List.copyOf(items), nextAfter);
    }

    @Transactional(readOnly = true)
    public EvalComparison compare(long baseId, long candidateId) {
        EvalRunRecord base = requireRun(baseId);
        EvalRunRecord candidate = requireRun(candidateId);
        requireComplete(base);
        requireComplete(candidate);
        List<EvalCaseView> baseCases = allCases(base);
        List<EvalCaseView> candidateCases = allCases(candidate);
        try {
            return comparisonService.compare(toView(base), baseCases, toView(candidate), candidateCases);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    private List<EvalCaseView> allCases(EvalRunRecord run) {
        List<EvalCaseRecord> records = caseRepository.findByRunIdOrderByPositionAsc(run.getId());
        if (records.size() != run.getCaseCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "stored case count differs from run metadata: " + run.getId());
        }
        List<EvalCaseView> cases = new ArrayList<>();
        for (EvalCaseRecord record : records) {
            cases.add(toCaseView(record));
        }
        return cases;
    }

    private void validateSummary(long runId, EvalRunRecord record, EvalRunSummary summary) {
        if (summary == null || summary.getRunId() == null || summary.getRunId() != runId) {
            throw new IllegalArgumentException("eval summary has wrong run ID");
        }
        if (summary.getTotal() != record.getCaseCount()
                || summary.getResults() == null
                || summary.getResults().size() != record.getCaseCount()) {
            throw new IllegalArgumentException("eval summary has incomplete case results");
        }
        Set<String> ids = new HashSet<>();
        for (EvalRunResult result : summary.getResults()) {
            if (result == null || !StringUtils.hasText(result.getId()) || !ids.add(result.getId())) {
                throw new IllegalArgumentException("eval summary has missing or duplicate case IDs");
            }
        }
    }

    private EvalRunRecord requireRun(long runId) {
        if (runId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "run ID must be positive");
        }
        return runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "eval run not found"));
    }

    private void requireComplete(EvalRunRecord run) {
        if (!COMPLETED.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "eval run is not completed");
        }
    }

    private void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private EvalRunView toView(EvalRunRecord record) {
        EvalRunSummary summary = record.getSummaryJson() == null
                ? null : readJson(record.getSummaryJson(), EvalRunSummary.class);
        return new EvalRunView(record.getId(), record.getStatus(), record.getQuestionSetSha256(),
                record.getStartedAt(), record.getCompletedAt(), record.getCaseCount(),
                summary == null ? 0 : summary.getBaselineHitCount(),
                summary == null ? 0 : summary.getGraphHitCount(),
                summary == null ? 0 : summary.getSourceHitCount(),
                summary == null ? 0 : summary.getWrongAnswerCount(),
                summary == null ? 0 : summary.getCitationMatchedCount(),
                summary == null ? 0 : summary.getCitationMissingCount(),
                summary == null ? 0 : summary.getAbstainedCount(),
                record.getErrorMessage());
    }

    private EvalCaseView toCaseView(EvalCaseRecord record) {
        EvalRunResult result = readJson(record.getResultJson(), EvalRunResult.class);
        if (!record.getCaseId().equals(result.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "stored case ID differs from snapshot: " + record.getId());
        }
        return new EvalCaseView(record.getCaseId(), record.getPosition(), result);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize fixed-eval snapshot", ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "stored fixed-eval snapshot is invalid", ex);
        }
    }
}
