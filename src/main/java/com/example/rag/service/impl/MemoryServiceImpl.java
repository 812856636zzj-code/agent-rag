package com.example.rag.service.impl;

import com.example.rag.dto.AgentExecutionTrace;
import com.example.rag.dto.AgentMemoryItem;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.entity.RagAgentMemory;
import com.example.rag.repository.RagAgentMemoryRepository;
import com.example.rag.service.MemoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MemoryServiceImpl implements MemoryService {

    private static final int DEFAULT_RELEVANT_LIMIT = 5;
    private static final int CANDIDATE_LIMIT = 30;

    private final RagAgentMemoryRepository memoryRepository;

    public MemoryServiceImpl(RagAgentMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Override
    public void saveFromTrace(AgentExecutionTrace trace) {
        if (trace == null || !StringUtils.hasText(trace.getTraceId())) {
            return;
        }
        upsertMemory("QUERY_HISTORY",
                normalizeKey(trace.getTraceId()),
                "traceId=" + trace.getTraceId() + ", steps=" + (trace.getSteps() == null ? 0 : trace.getSteps().size()),
                null,
                null,
                null,
                "TRACE",
                trace.getTraceId(),
                0.4d);
    }

    @Override
    public void saveFromTrace(AgentExecutionTrace trace, AskResponse response, QueryUnderstanding understanding) {
        if (response == null || !StringUtils.hasText(response.getQuestion())) {
            saveFromTrace(trace);
            return;
        }
        String intent = understanding == null || understanding.getIntent() == null ? null : understanding.getIntent().name();
        String traceId = trace == null ? null : trace.getTraceId();
        upsertMemory("QUERY_HISTORY",
                normalizeKey(response.getQuestion()),
                response.getQuestion(),
                intent,
                null,
                null,
                "TRACE",
                traceId,
                0.7d);

        for (String entity : extractEntityNames(understanding, response)) {
            upsertMemory("ENTITY_FOCUS",
                    normalizeKey(entity),
                    entity,
                    intent,
                    entity,
                    null,
                    "TRACE",
                    traceId,
                    0.75d);
        }

        if (StringUtils.hasText(response.getAnswer())) {
            upsertMemory("SUCCESS_CASE",
                    normalizeKey(response.getQuestion()),
                    summarizeAnswer(response.getQuestion(), response.getAnswer()),
                    intent,
                    firstEntityName(understanding, response),
                    null,
                    "TRACE",
                    traceId,
                    0.8d);
        }
    }

    @Override
    public void saveFromEvalResult(EvalRunResult evalResult) {
        if (evalResult == null || evalResult.getDiagnosisReason() == null) {
            return;
        }
        String question = firstNonEmpty(evalResult.getOriginalQuestion(), evalResult.getQuestion());
        String intent = firstNonEmpty(evalResult.getDetectedIntent(), evalResult.getGraphDetectedIntent());
        String entity = firstDetectedEntity(evalResult);
        for (String reason : evalResult.getDiagnosisReason()) {
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            if (isFailureReason(reason)) {
                upsertMemory("FAILURE_CASE",
                        normalizeKey(question + "|" + reason),
                        buildFailureMemoryValue(evalResult, reason),
                        intent,
                        entity,
                        reason,
                        "EVAL",
                        evalResult.getId(),
                        0.85d);
                String rule = proceduralRuleFor(reason);
                if (StringUtils.hasText(rule)) {
                    upsertMemory("PROCEDURAL_RULE",
                            normalizeKey(reason),
                            rule,
                            intent,
                            null,
                            reason,
                            "EVAL",
                            evalResult.getId(),
                            0.9d);
                }
            }
        }
    }

    @Override
    public List<AgentMemoryItem> findRelevantMemories(String question, String intent, List<String> detectedEntities, String failureType) {
        String questionLike = "%" + normalizeLikeTerm(question) + "%";
        List<String> entities = normalizeEntities(detectedEntities);
        List<RagAgentMemory> memories = memoryRepository.findRelevant(
                normalizeIntent(intent),
                StringUtils.hasText(failureType) ? failureType : null,
                questionLike,
                entities,
                PageRequest.of(0, CANDIDATE_LIMIT)
        );
        List<ScoredMemory> scoredMemories = new ArrayList<>();
        for (RagAgentMemory memory : memories) {
            ScoredMemory scored = scoreMemory(memory, question, normalizeIntent(intent), entities, failureType);
            if (scored.score > 0.0d) {
                scoredMemories.add(scored);
            }
        }
        scoredMemories.sort(Comparator
                .comparingDouble(ScoredMemory::getScore).reversed()
                .thenComparing((ScoredMemory scored) -> scored.memory.getConfidence() == null ? 0.0d : scored.memory.getConfidence(), Comparator.reverseOrder())
                .thenComparing((ScoredMemory scored) -> scored.memory.getUsageCount() == null ? 0 : scored.memory.getUsageCount(), Comparator.reverseOrder()));

        List<AgentMemoryItem> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ScoredMemory scored : scoredMemories) {
            if (items.size() >= DEFAULT_RELEVANT_LIMIT) {
                break;
            }
            RagAgentMemory memory = scored.memory;
            memory.setUsageCount((memory.getUsageCount() == null ? 0 : memory.getUsageCount()) + 1);
            memory.setLastUsedAt(now);
            memory.setUpdatedAt(now);
            memoryRepository.save(memory);
            AgentMemoryItem item = toItem(memory);
            item.setMatchScore(scored.score);
            item.setMemoryMatchReason(String.join(",", scored.reasons));
            items.add(item);
        }
        return items;
    }

    @Override
    public List<AgentMemoryItem> listRecentMemories(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_RELEVANT_LIMIT : Math.min(limit, 50);
        List<RagAgentMemory> memories = memoryRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, safeLimit));
        List<AgentMemoryItem> items = new ArrayList<>();
        for (RagAgentMemory memory : memories) {
            items.add(toItem(memory));
        }
        return items;
    }

    private void upsertMemory(String type,
                              String key,
                              String value,
                              String intent,
                              String entityName,
                              String failureType,
                              String sourceType,
                              String sourceId,
                              double confidence) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(key)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RagAgentMemory> duplicates = memoryRepository.findDuplicates(type, key, emptyToNull(entityName), emptyToNull(failureType));
        RagAgentMemory memory = duplicates.isEmpty() ? new RagAgentMemory() : duplicates.get(0);
        if (memory.getId() == null) {
            memory.setCreatedAt(now);
            memory.setUsageCount(0);
        }
        memory.setMemoryType(type);
        memory.setMemoryKey(key);
        memory.setMemoryValue(trim(value, 2000));
        memory.setIntent(emptyToNull(intent));
        memory.setEntityName(emptyToNull(entityName));
        memory.setFailureType(emptyToNull(failureType));
        memory.setSourceType(emptyToNull(sourceType));
        memory.setSourceId(emptyToNull(sourceId));
        memory.setConfidence(confidence);
        memory.setUsageCount((memory.getUsageCount() == null ? 0 : memory.getUsageCount()) + 1);
        memory.setLastUsedAt(now);
        memory.setUpdatedAt(now);
        memoryRepository.save(memory);
    }

    private List<String> extractEntityNames(QueryUnderstanding understanding, AskResponse response) {
        Set<String> entities = new LinkedHashSet<>();
        if (understanding != null) {
            addIfText(entities, understanding.getFocusEntity());
            if (understanding.getQueryEntities() != null) {
                for (QueryEntity entity : understanding.getQueryEntities()) {
                    if (entity != null) {
                        addIfText(entities, firstNonEmpty(entity.getText(), entity.getNormalizedText()));
                    }
                }
            }
        }
        if (response != null && response.getQueryEntities() != null) {
            for (QueryEntity entity : response.getQueryEntities()) {
                if (entity != null) {
                    addIfText(entities, firstNonEmpty(entity.getText(), entity.getNormalizedText()));
                }
            }
        }
        return new ArrayList<>(entities);
    }

    private String firstEntityName(QueryUnderstanding understanding, AskResponse response) {
        List<String> entities = extractEntityNames(understanding, response);
        return entities.isEmpty() ? null : entities.get(0);
    }

    private String firstDetectedEntity(EvalRunResult evalResult) {
        if (evalResult.getDetectedEntities() != null && !evalResult.getDetectedEntities().isEmpty()) {
            return evalResult.getDetectedEntities().get(0);
        }
        if (evalResult.getGraphDetectedEntities() != null && !evalResult.getGraphDetectedEntities().isEmpty()) {
            return evalResult.getGraphDetectedEntities().get(0);
        }
        return null;
    }

    private boolean isFailureReason(String reason) {
        return !"GRAPH_BETTER_THAN_BASELINE".equals(reason)
                && !"GRAPH_HIT".equals(reason)
                && !"NO_DIAGNOSIS".equals(reason);
    }

    private String proceduralRuleFor(String reason) {
        if ("NO_CANDIDATES_RECALLED".equals(reason)) {
            return "When no candidates are recalled, expand recall through query rewrite, single entity fallback, and intent keyword fallback.";
        }
        if ("SOURCE_NOT_MATCHED".equals(reason)) {
            return "When source is not matched, inspect source assembly and relation evidence selection before answer assembly.";
        }
        if ("ANSWER_MISSING_EXPECTED_KEYWORDS".equals(reason) || "ANSWER_MISSING_KEYWORD".equals(reason)) {
            return "When answer misses expected keywords, inspect retrieved context coverage and answer builder extraction rules.";
        }
        if ("RERANK_NO_EFFECT".equals(reason) || reason.startsWith("RERANK_")) {
            return "When rerank has no effect, inspect candidate diversity, score spread, and scoring weights.";
        }
        if ("REWRITE_NO_EFFECT".equals(reason)) {
            return "When rewrite has no effect, extend synonym and entity-intent query expansion rules.";
        }
        return "Review eval failure reason " + reason + " and decide whether recall, rerank, source evidence, or answer assembly should be tuned.";
    }

    private String buildFailureMemoryValue(EvalRunResult result, String reason) {
        return "question=" + firstNonEmpty(result.getOriginalQuestion(), result.getQuestion())
                + "; reason=" + reason
                + "; missing=" + join(result.getMissingExpectedKeywords())
                + "; rewrittenQuery=" + firstNonEmpty(result.getRewrittenQuery(), result.getGraphRewrittenQuery())
                + "; sourceHit=" + result.isSourceHit()
                + "; graphHit=" + result.isGraphHit();
    }

    private AgentMemoryItem toItem(RagAgentMemory memory) {
        AgentMemoryItem item = new AgentMemoryItem();
        item.setId(memory.getId());
        item.setMemoryType(memory.getMemoryType());
        item.setMemoryKey(memory.getMemoryKey());
        item.setMemoryValue(memory.getMemoryValue());
        item.setIntent(memory.getIntent());
        item.setEntityName(memory.getEntityName());
        item.setFailureType(memory.getFailureType());
        item.setSourceType(memory.getSourceType());
        item.setSourceId(memory.getSourceId());
        item.setConfidence(memory.getConfidence());
        item.setUsageCount(memory.getUsageCount());
        return item;
    }

    private ScoredMemory scoreMemory(RagAgentMemory memory, String question, String intent, List<String> entities, String failureType) {
        ScoredMemory scored = new ScoredMemory(memory);
        if (memory == null) {
            return scored;
        }
        boolean specificMatch = false;
        String normalizedQuestion = normalizeKey(question);
        if (StringUtils.hasText(normalizedQuestion)
                && StringUtils.hasText(memory.getMemoryKey())
                && (memory.getMemoryKey().contains(normalizedQuestion) || normalizedQuestion.contains(memory.getMemoryKey()))) {
            scored.add(2.0d, "question key match");
            specificMatch = true;
        }
        if (StringUtils.hasText(intent) && intent.equals(memory.getIntent())) {
            scored.add(3.0d, "intent match");
        }
        if (StringUtils.hasText(memory.getEntityName())
                && entities.contains(memory.getEntityName().toLowerCase(Locale.ROOT))) {
            scored.add(3.0d, "entity match");
            specificMatch = true;
        }
        if (StringUtils.hasText(failureType) && failureType.equals(memory.getFailureType())) {
            scored.add(3.0d, "failureType match");
            specificMatch = true;
        }
        if (memory.getUpdatedAt() != null && memory.getUpdatedAt().isAfter(LocalDateTime.now().minusDays(1))) {
            scored.add(1.0d, "recent");
        }
        if (!specificMatch) {
            scored.score = 0.0d;
            scored.reasons.clear();
        }
        return scored;
    }

    private List<String> normalizeEntities(List<String> detectedEntities) {
        List<String> entities = new ArrayList<>();
        if (detectedEntities != null) {
            for (String entity : detectedEntities) {
                if (StringUtils.hasText(entity)) {
                    entities.add(entity.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (entities.isEmpty()) {
            entities.add("__no_entity__");
        }
        return entities;
    }

    private String normalizeIntent(String intent) {
        if (!StringUtils.hasText(intent) || "UNKNOWN".equalsIgnoreCase(intent)) {
            return null;
        }
        return intent;
    }

    private String normalizeLikeTerm(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return trim(value.trim().toLowerCase(Locale.ROOT), 255);
    }

    private void addIfText(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private String summarizeAnswer(String question, String answer) {
        return "question=" + question + "; answer=" + trim(answer, 500);
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String firstNonEmpty(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private static class ScoredMemory {
        private final RagAgentMemory memory;
        private final List<String> reasons = new ArrayList<>();
        private double score;

        private ScoredMemory(RagAgentMemory memory) {
            this.memory = memory;
        }

        private void add(double value, String reason) {
            score += value;
            reasons.add(reason);
        }

        private double getScore() {
            return score;
        }
    }
}
