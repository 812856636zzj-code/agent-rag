package com.example.rag.service.impl;

import com.example.rag.dto.Day6GraphEvalQuestion;
import com.example.rag.dto.Day6GraphEvalResult;
import com.example.rag.dto.Day6AgentToolEvalQuestion;
import com.example.rag.dto.Day6AgentToolEvalResult;
import com.example.rag.dto.AgentExecutionStep;
import com.example.rag.dto.AgentRouteDecision;
import com.example.rag.dto.Day7EvalQuestion;
import com.example.rag.dto.Day7EvalResult;
import com.example.rag.dto.EvalRunQuestion;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunSummary;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.RetrievalScoreDetail;
import com.example.rag.dto.SourceItem;
import com.example.rag.enums.QuestionType;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentExecutor;
import com.example.rag.service.AgentRouterService;
import com.example.rag.service.AskService;
import com.example.rag.service.EvalService;
import com.example.rag.service.MemoryService;
import com.example.rag.service.QuestionClassifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EvalServiceImpl implements EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalServiceImpl.class);
    private static final String DAY6_EVAL_RESOURCE = "eval/day6_graph_eval_questions.json";
    private static final Path DAY6_EVAL_MARKDOWN_PATH = Paths.get("docs", "eval", "day6_graph_eval_report.md");
    private static final Path DAY6_EVAL_RAW_RESULT_PATH = Paths.get("docs", "eval", "day6_graph_eval_results.json");
    private static final String DAY6_AGENT_TOOL_EVAL_RESOURCE = "eval/day6_agent_tool_eval_questions.json";
    private static final Path DAY6_AGENT_TOOL_EVAL_MARKDOWN_PATH = Paths.get("docs", "eval", "day6_agent_tool_eval_report.md");
    private static final Path DAY6_AGENT_TOOL_EVAL_RAW_RESULT_PATH = Paths.get("docs", "eval", "day6_agent_tool_eval_results.json");
    private static final String DAY7_EVAL_RESOURCE = "eval/day7_eval_questions.json";
    private static final Path DAY7_EVAL_MARKDOWN_PATH = Paths.get("docs", "eval", "eval_result.md");
    private static final Path DAY7_EVAL_RAW_RESULT_PATH = Paths.get("docs", "eval", "day7_raw_results.json");
    private static final Path LATEST_EVAL_QUESTIONS_PATH = Paths.get("docs", "eval", "questions.json");
    private static final Path LATEST_EVAL_REPORT_PATH = Paths.get("docs", "eval", "latest_eval_report.md");
    private static final Path LATEST_EVAL_RESULTS_PATH = Paths.get("docs", "eval", "latest_eval_results.json");

    private final AskService askService;
    private final QuestionClassifier questionClassifier;
    private final AgentRouterService agentRouterService;
    private final AgentExecutor agentExecutor;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    public EvalServiceImpl(AskService askService,
                           QuestionClassifier questionClassifier,
                           AgentRouterService agentRouterService,
                           AgentExecutor agentExecutor,
                           MemoryService memoryService,
                           ObjectMapper objectMapper) {
        this.askService = askService;
        this.questionClassifier = questionClassifier;
        this.agentRouterService = agentRouterService;
        this.agentExecutor = agentExecutor;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvalRunSummary runEval() {
        List<EvalRunQuestion> questions = loadEvalRunQuestions();
        List<EvalRunResult> results = new ArrayList<>();
        for (EvalRunQuestion question : questions) {
            results.add(runEvalQuestion(question));
        }
        EvalRunSummary summary = buildEvalRunSummary(results);
        writeEvalRunResults(results);
        writeEvalRunReport(summary);
        return summary;
    }

    @Override
    public List<Day6GraphEvalResult> runDay6GraphEval() {
        List<Day6GraphEvalQuestion> questions = loadDay6Questions();
        List<Day6GraphEvalResult> results = new ArrayList<>();
        for (Day6GraphEvalQuestion question : questions) {
            results.add(runDay6SingleQuestion(question));
        }
        writeDay6RawResults(results);
        writeDay6Markdown(results);
        return results;
    }

    private EvalRunResult runEvalQuestion(EvalRunQuestion evalQuestion) {
        AskResponse baseline = safeAsk(evalQuestion.getQuestion(), "baseline");
        AskResponse graph = safeAsk(evalQuestion.getQuestion(), "graph");

        EvalRunResult result = new EvalRunResult();
        result.setId(evalQuestion.getId());
        result.setOriginalQuestion(evalQuestion.getQuestion());
        result.setQuestion(evalQuestion.getQuestion());
        result.setType(evalQuestion.getType());
        result.setExpectedKeywords(evalQuestion.getExpectedKeywords());
        result.setExpectedSource(evalQuestion.getExpectedSource());
        result.setBaselineAnswer(baseline.getAnswer());
        result.setGraphAnswer(graph.getAnswer());
        result.setBaselineHit(isEvalAnswerHit(baseline, evalQuestion.getExpectedKeywords()));
        result.setGraphHit(isEvalAnswerHit(graph, evalQuestion.getExpectedKeywords()));
        result.setSourceHit(isEvalSourceHit(graph, evalQuestion.getExpectedSource()));
        result.setBaselineNoise(hasNoise(baseline));
        result.setGraphNoise(hasNoise(graph));
        result.setBaselineReadabilityScore(scoreReadability(baseline));
        result.setGraphReadabilityScore(scoreReadability(graph));
        fillEvalRunGraphDebug(result, graph);
        fillEvalRunDiagnosis(result, baseline, graph);
        result.setNotes(buildEvalRunNotes(result, baseline, graph));
        memoryService.saveFromEvalResult(result);
        return result;
    }

    @Override
    public List<Day6AgentToolEvalResult> runDay6AgentToolEval() {
        List<Day6AgentToolEvalQuestion> questions = loadDay6AgentToolQuestions();
        List<Day6AgentToolEvalResult> results = new ArrayList<>();
        for (Day6AgentToolEvalQuestion question : questions) {
            results.add(runDay6AgentToolSingleQuestion(question));
        }
        writeDay6AgentToolRawResults(results);
        writeDay6AgentToolMarkdown(results);
        return results;
    }

    @Override
    public List<Day7EvalResult> runDay7Eval() {
        List<Day7EvalQuestion> questions = loadQuestions();
        List<Day7EvalResult> results = new ArrayList<>();
        for (Day7EvalQuestion question : questions) {
            results.add(runSingleQuestion(question));
        }
        writeRawResults(results);
        writeEvalMarkdown(results);
        return results;
    }

    private Day7EvalResult runSingleQuestion(Day7EvalQuestion evalQuestion) {
        AskResponse baseline = safeAsk(evalQuestion.getQuestion(), "baseline");
        AskResponse graph = safeAsk(evalQuestion.getQuestion(), "graph");

        Day7EvalResult result = new Day7EvalResult();
        result.setId(evalQuestion.getId());
        result.setQuestion(evalQuestion.getQuestion());
        result.setExpectedAnswer(evalQuestion.getExpectedAnswer());
        result.setCategory(evalQuestion.getCategory());
        result.setBaselineAnswer(baseline.getAnswer());
        result.setGraphAnswer(graph.getAnswer());
        result.setBaselineHit(isBaselineHit(evalQuestion, baseline));
        result.setGraphHit(isGraphHit(evalQuestion, graph));
        result.setBaselineReadabilityScore(scoreReadability(baseline));
        result.setGraphReadabilityScore(scoreReadability(graph));
        result.setBaselineNoise(hasNoise(baseline));
        result.setGraphNoise(hasNoise(graph));
        result.setNotes(buildNotes(result, baseline, graph));
        return result;
    }

    private Day6GraphEvalResult runDay6SingleQuestion(Day6GraphEvalQuestion evalQuestion) {
        AskResponse baseline = safeAsk(evalQuestion.getQuestion(), "baseline");
        AskResponse graph = safeAsk(evalQuestion.getQuestion(), "graph");
        int baselineKeywordHitCount = countMatchedKeywords(baseline.getAnswer(), evalQuestion.getExpectedKeywords());
        int graphKeywordHitCount = countMatchedKeywords(graph.getAnswer(), evalQuestion.getExpectedKeywords());

        Day6GraphEvalResult result = new Day6GraphEvalResult();
        result.setId(evalQuestion.getId());
        result.setQuestion(evalQuestion.getQuestion());
        result.setQuestionType(evalQuestion.getQuestionType());
        result.setExpectedMode(evalQuestion.getExpectedMode());
        result.setExpectedKeywords(evalQuestion.getExpectedKeywords());
        result.setExpectedRelations(evalQuestion.getExpectedRelations());
        result.setBaselineAnswer(baseline.getAnswer());
        result.setGraphAnswer(graph.getAnswer());
        result.setBaselineHit(isDay6BaselineHit(evalQuestion, baseline, baselineKeywordHitCount));
        result.setGraphHit(isDay6GraphHit(evalQuestion, graph));
        result.setRelationHit(matchesExpectedRelations(graph, evalQuestion.getExpectedRelations()));
        result.setSourceHit(matchesSources(graph, evalQuestion.getExpectedKeywords()));
        result.setEvidenceHit(matchesEvidence(graph, evalQuestion.getExpectedKeywords()));
        result.setExpectedModeHit(matchesExpectedMode(graph, evalQuestion.getExpectedMode()));
        result.setNotes(buildDay6Notes(evalQuestion, baseline, graph, result, baselineKeywordHitCount, graphKeywordHitCount));
        return result;
    }

    private Day6AgentToolEvalResult runDay6AgentToolSingleQuestion(Day6AgentToolEvalQuestion evalQuestion) {
        AskResponse routerOnly = runRouterOnly(evalQuestion.getQuestion());
        AskResponse toolAgent = safeAsk(evalQuestion.getQuestion(), null);

        Day6AgentToolEvalResult result = new Day6AgentToolEvalResult();
        result.setId(evalQuestion.getId());
        result.setQuestion(evalQuestion.getQuestion());
        result.setQuestionType(evalQuestion.getQuestionType());
        result.setExpectedTools(evalQuestion.getExpectedTools());
        result.setRouterOnlyAnswer(routerOnly.getAnswer());
        result.setToolAgentAnswer(toolAgent.getAnswer());
        result.setRouterOnlyHit(matchesExpectedKeywords(routerOnly.getAnswer(), evalQuestion.getExpectedKeywords()));
        result.setToolAgentHit(matchesExpectedKeywords(toolAgent.getAnswer(), evalQuestion.getExpectedKeywords()));
        result.setGraphHit(hasTool(toolAgent, "GRAPH_SEARCH"));
        result.setSqlSearchHit(isSqlSearchHit(toolAgent));
        result.setChunkSearchHit(hasTool(toolAgent, "CHUNK_SEARCH"));
        result.setTraceHit(hasTraceHit(toolAgent, evalQuestion.getExpectedTools()));
        result.setSourceHit(matchesSourceTypes(toolAgent, evalQuestion.getExpectedSourceTypes()));
        result.setEvidenceHit(matchesEvidence(toolAgent, evalQuestion.getExpectedKeywords()));
        result.setExpectedToolsHit(hasExpectedTools(toolAgent, evalQuestion.getExpectedTools()));
        result.setNotes(buildDay6AgentToolNotes(evalQuestion, routerOnly, toolAgent, result));
        return result;
    }

    private AskResponse safeAsk(String question, String mode) {
        try {
            return askService.ask(question, mode);
        } catch (RuntimeException ex) {
            log.error("run eval ask failed, mode = {}, question = {}", mode, question, ex);
            AskResponse response = new AskResponse();
            response.setQuestion(question);
            response.setRetrievalMode(mode == null ? "" : mode.toUpperCase(Locale.ROOT));
            response.setAnswer("");
            return response;
        }
    }

    private List<Day7EvalQuestion> loadQuestions() {
        ClassPathResource resource = new ClassPathResource(DAY7_EVAL_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<Day7EvalQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("load day7 eval questions failed", ex);
        }
    }

    private List<EvalRunQuestion> loadEvalRunQuestions() {
        if (!Files.exists(LATEST_EVAL_QUESTIONS_PATH)) {
            throw new IllegalStateException("eval questions file not found: " + LATEST_EVAL_QUESTIONS_PATH.toAbsolutePath());
        }
        try (InputStream inputStream = Files.newInputStream(LATEST_EVAL_QUESTIONS_PATH)) {
            return objectMapper.readValue(inputStream, new TypeReference<List<EvalRunQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("load eval questions failed", ex);
        }
    }

    private List<Day6GraphEvalQuestion> loadDay6Questions() {
        ClassPathResource resource = new ClassPathResource(DAY6_EVAL_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<Day6GraphEvalQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("load day6 eval questions failed", ex);
        }
    }

    private List<Day6AgentToolEvalQuestion> loadDay6AgentToolQuestions() {
        ClassPathResource resource = new ClassPathResource(DAY6_AGENT_TOOL_EVAL_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<Day6AgentToolEvalQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("load day6 agent tool eval questions failed", ex);
        }
    }

    private AskResponse runRouterOnly(String question) {
        try {
            QuestionType questionType = questionClassifier.classify(question);
            AgentRouteDecision routeDecision = agentRouterService.route(question, questionType);
            routeDecision.setSelectedTools(removeSqlSearch(routeDecision.getSelectedTools()));
            AskResponse response = agentExecutor.execute(question, routeDecision);
            if (response.getDebug() != null && StringUtils.hasText(response.getDebug().getRetrievalType())) {
                response.getDebug().setRetrievalType(response.getDebug().getRetrievalType() + "_ROUTER_ONLY");
            }
            return response;
        } catch (RuntimeException ex) {
            log.error("run router-only eval failed, question = {}", question, ex);
            AskResponse response = new AskResponse();
            response.setQuestion(question);
            response.setAnswer("");
            response.setRetrievalMode("ROUTER_ONLY");
            return response;
        }
    }

    private List<ToolType> removeSqlSearch(List<ToolType> tools) {
        List<ToolType> filtered = new ArrayList<>();
        if (tools == null) {
            return filtered;
        }
        for (ToolType tool : tools) {
            if (tool != ToolType.SQL_SEARCH) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    private boolean isBaselineHit(Day7EvalQuestion question, AskResponse response) {
        return containsAnyIgnoreCase(response.getAnswer(), question.getExpectedKeywords());
    }

    private boolean isDay6BaselineHit(Day6GraphEvalQuestion question, AskResponse response, int keywordHitCount) {
        if (isTableFieldQuestion(question)) {
            return meetsTableFieldThreshold(keywordHitCount, question.getExpectedKeywords());
        }
        return matchesExpectedKeywords(response.getAnswer(), question.getExpectedKeywords());
    }

    private boolean isDay6GraphHit(Day6GraphEvalQuestion question, AskResponse response) {
        int keywordHitCount = countMatchedKeywords(response.getAnswer(), question.getExpectedKeywords());
        if (isTableFieldQuestion(question)) {
            if (!meetsTableFieldThreshold(keywordHitCount, question.getExpectedKeywords())) {
                return false;
            }
            if (containsDirtyFieldTerms(response.getAnswer())) {
                return false;
            }
            if (containsAnyIgnoreCase(response.getAnswer(), listOf("searchHybrid", "focusEntity"))) {
                return false;
            }
            if (question.getExpectedRelations() != null
                    && question.getExpectedRelations().contains("TABLE_HAS_FIELD")
                    && !matchesExpectedRelations(response, question.getExpectedRelations())) {
                return false;
            }
            return keywordHitCount > 1;
        }
        return matchesExpectedKeywords(response.getAnswer(), question.getExpectedKeywords());
    }

    private boolean isGraphHit(Day7EvalQuestion question, AskResponse response) {
        if (containsAnyIgnoreCase(response.getAnswer(), question.getExpectedKeywords())) {
            return true;
        }
        return containsAnyIgnoreCase(joinStrings(extractRelationOrSourceTerms(response)), question.getExpectedEntities())
                || containsAnyIgnoreCase(joinStrings(extractRelationSummaries(response)), question.getExpectedRelations());
    }

    private int scoreReadability(AskResponse response) {
        String answer = response == null ? "" : response.getAnswer();
        if (!StringUtils.hasText(answer)) {
            return 0;
        }
        if (containsAnyIgnoreCase(answer, listOf("oracle.sql.CLOB", "[object Object]"))) {
            return 1;
        }
        if (answer.length() < 20 || !containsSentenceBreak(answer)) {
            return 2;
        }
        if (containsAllIgnoreCase(answer, listOf("结论", "命中", "证据"))) {
            if (StringUtils.hasText(joinStrings(extractRelationSummaries(response)))
                    || StringUtils.hasText(joinStrings(extractRelationOrSourceTerms(response)))) {
                return 5;
            }
            return 4;
        }
        return 3;
    }

    private boolean hasNoise(AskResponse response) {
        String merged = joinStrings(listOf(
                response == null ? "" : response.getAnswer(),
                response == null ? "" : response.getStructuredContext(),
                response == null ? "" : joinStrings(extractRelationSummaries(response)),
                response == null ? "" : joinStrings(extractRelationOrSourceTerms(response))
        ));
        return containsAnyIgnoreCase(merged, listOf(
                "预期 intent",
                "系统应该命中",
                "验收问题",
                "oracle.sql.CLOB",
                "[object Object]",
                "TEST_SPEC"
        ));
    }

    private String buildNotes(Day7EvalResult result, AskResponse baseline, AskResponse graph) {
        List<String> notes = new ArrayList<>();
        if (!result.isBaselineHit() && result.isGraphHit()) {
            notes.add("graph improved hit");
        }
        if (result.getGraphReadabilityScore() > result.getBaselineReadabilityScore()) {
            notes.add("graph readability higher");
        }
        if (result.isBaselineNoise() && !result.isGraphNoise()) {
            notes.add("graph reduced noise");
        }
        if (!StringUtils.hasText(graph.getAnswer())) {
            notes.add("graph answer empty");
        }
        if (!StringUtils.hasText(baseline.getAnswer())) {
            notes.add("baseline answer empty");
        }
        return String.join("; ", notes);
    }

    private boolean isEvalAnswerHit(AskResponse response, List<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return StringUtils.hasText(response == null ? "" : response.getAnswer());
        }
        int hitCount = countMatchedKeywords(buildEvalSearchText(response), expectedKeywords);
        if (expectedKeywords.size() <= 2) {
            return hitCount == expectedKeywords.size();
        }
        return ((double) hitCount / expectedKeywords.size()) >= 0.6d;
    }

    private boolean isEvalSourceHit(AskResponse response, List<String> expectedSource) {
        if (expectedSource == null || expectedSource.isEmpty()) {
            return response != null && response.getSources() != null && !response.getSources().isEmpty();
        }
        return countMatchedKeywords(buildEvalSourceText(response), expectedSource) > 0;
    }

    private String buildEvalSearchText(AskResponse response) {
        if (response == null) {
            return "";
        }
        return joinStrings(listOf(
                response.getAnswer(),
                response.getStructuredContext(),
                joinStrings(extractRelationSummaries(response)),
                joinStrings(extractRelationOrSourceTerms(response)),
                buildEvalSourceText(response)
        ));
    }

    private String buildEvalSourceText(AskResponse response) {
        if (response == null || response.getSources() == null) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (SourceItem source : response.getSources()) {
            if (source == null) {
                continue;
            }
            values.add(source.getDocumentName());
            values.add(source.getSourceType());
            values.add(source.getContent());
            values.add(joinStrings(source.getEvidenceSentences()));
        }
        return joinStrings(values);
    }

    private String buildEvalRunNotes(EvalRunResult result, AskResponse baseline, AskResponse graph) {
        List<String> notes = new ArrayList<>();
        if (!result.isBaselineHit() && result.isGraphHit()) {
            notes.add("graph improved keyword hit");
        }
        if (result.isBaselineHit() && !result.isGraphHit()) {
            notes.add("graph regressed keyword hit");
        }
        if (result.getGraphReadabilityScore() > result.getBaselineReadabilityScore()) {
            notes.add("graph readability higher");
        }
        if (result.isGraphNoise()) {
            notes.add("graph noise detected");
        }
        if (result.isBaselineNoise()) {
            notes.add("baseline noise detected");
        }
        if (!result.isSourceHit()) {
            notes.add("expected source not found in graph sources");
        }
        if (!StringUtils.hasText(baseline == null ? "" : baseline.getAnswer())) {
            notes.add("baseline answer empty");
        }
        if (!StringUtils.hasText(graph == null ? "" : graph.getAnswer())) {
            notes.add("graph answer empty");
        }
        return String.join("; ", notes);
    }

    private void fillEvalRunGraphDebug(EvalRunResult result, AskResponse graph) {
        if (result == null || graph == null || graph.getDebug() == null) {
            return;
        }
        result.setRewrittenQuery(graph.getDebug().getRewrittenQuery());
        result.setExpandedKeywords(graph.getDebug().getExpandedKeywords());
        result.setSearchQueries(graph.getDebug().getSearchQueries());
        result.setMatchedQueries(graph.getDebug().getMatchedQueries());
        result.setRecallFallbackUsed(graph.getDebug().isRecallFallbackUsed());
        result.setCandidateCountByQuery(graph.getDebug().getCandidateCountByQuery());
        result.setCandidateCountAfterMerge(graph.getDebug().getCandidateCountAfterMerge());
        result.setDuplicateCandidateRemovedCount(graph.getDebug().getDuplicateCandidateRemovedCount());
        result.setDetectedIntent(graph.getDebug().getDetectedIntent());
        result.setDetectedEntities(graph.getDebug().getDetectedEntities());
        result.setGraphRewrittenQuery(graph.getDebug().getRewrittenQuery());
        result.setGraphExpandedKeywords(graph.getDebug().getExpandedKeywords());
        result.setGraphDetectedIntent(graph.getDebug().getDetectedIntent());
        result.setGraphDetectedEntities(graph.getDebug().getDetectedEntities());
        result.setTopScoreDetails(graph.getDebug().getTopScoreDetails());
        result.setRerankBeforeChunkIds(graph.getDebug().getRerankBeforeChunkIds());
        result.setRerankAfterChunkIds(graph.getDebug().getRerankAfterChunkIds());
        result.setTopChunksBeforeRerank(graph.getDebug().getRerankBeforeChunkIds());
        result.setTopChunksAfterRerank(graph.getDebug().getRerankAfterChunkIds());
        result.setCandidateCountBeforeRerank(graph.getDebug().getRerankBeforeChunkIds() == null ? 0 : graph.getDebug().getRerankBeforeChunkIds().size());
        result.setCandidateCountAfterRerank(graph.getDebug().getRerankAfterChunkIds() == null ? 0 : graph.getDebug().getRerankAfterChunkIds().size());
        result.setUniqueCandidateCount(graph.getDebug().getUniqueCandidateCount());
        result.setCandidateScoreSpread(graph.getDebug().getCandidateScoreSpread());
        result.setTop1Score(graph.getDebug().getTop1Score());
        result.setTop2Score(graph.getDebug().getTop2Score());
        result.setTopScoreGap(graph.getDebug().getTopScoreGap());
        result.setRerankChanged(graph.getDebug().isRerankChanged());
        result.setRerankChangeReason(graph.getDebug().getRerankChangeReason());
        result.setAnswerProvider(graph.getDebug().getAnswerProvider());
        result.setModelName(graph.getDebug().getAnswerModelName());
        result.setAnswerLatencyMs(graph.getDebug().getAnswerLatencyMs());
        result.setFallbackUsed(graph.getDebug().isAnswerFallbackUsed());
    }

    private void fillEvalRunDiagnosis(EvalRunResult result, AskResponse baseline, AskResponse graph) {
        result.setSourceMatched(result.isSourceHit());
        result.setMissingExpectedKeywords(findMissingExpectedKeywords(graph == null ? "" : graph.getAnswer(), result.getExpectedKeywords()));
        result.setDiagnosisReason(buildDiagnosisReasons(result, baseline, graph));
    }

    private List<String> findMissingExpectedKeywords(String answer, List<String> expectedKeywords) {
        List<String> missing = new ArrayList<>();
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return missing;
        }
        String lowerAnswer = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        for (String keyword : expectedKeywords) {
            if (StringUtils.hasText(keyword) && !lowerAnswer.contains(keyword.toLowerCase(Locale.ROOT))) {
                missing.add(keyword);
            }
        }
        return missing;
    }

    private List<String> buildDiagnosisReasons(EvalRunResult result, AskResponse baseline, AskResponse graph) {
        List<String> reasons = new ArrayList<>();
        if (result.getCandidateCountBeforeRerank() <= 0) {
            reasons.add("NO_CANDIDATES_RECALLED");
        }
        if (isRewriteNoEffect(result)) {
            reasons.add("REWRITE_NO_EFFECT");
        }
        if (isRerankNoEffect(result)) {
            reasons.add("RERANK_NO_EFFECT");
        }
        String rerankReason = rerankDiagnosisReason(result.getRerankChangeReason());
        if (StringUtils.hasText(rerankReason)) {
            reasons.add(rerankReason);
        }
        if (!result.isSourceMatched()) {
            reasons.add("SOURCE_NOT_MATCHED");
        }
        if (result.getMissingExpectedKeywords() != null && !result.getMissingExpectedKeywords().isEmpty()) {
            reasons.add("ANSWER_MISSING_EXPECTED_KEYWORDS");
        }
        if (result.isGraphHit() && !result.isSourceMatched()) {
            reasons.add("ANSWER_RIGHT_SOURCE_WRONG");
        }
        if (result.isBaselineHit() && !result.isGraphHit()) {
            reasons.add("GRAPH_WORSE_THAN_BASELINE");
        }
        if (!result.isBaselineHit() && result.isGraphHit()) {
            reasons.add("GRAPH_BETTER_THAN_BASELINE");
        }
        return reasons;
    }

    private boolean isRewriteNoEffect(EvalRunResult result) {
        String original = normalizeForDiagnosis(result.getOriginalQuestion());
        String rewritten = normalizeForDiagnosis(result.getRewrittenQuery());
        if (!StringUtils.hasText(rewritten) || original.equals(rewritten)) {
            return true;
        }
        if (result.getExpandedKeywords() == null || result.getExpandedKeywords().isEmpty()) {
            return true;
        }
        for (String keyword : result.getExpandedKeywords()) {
            if (StringUtils.hasText(keyword) && !original.contains(normalizeForDiagnosis(keyword))) {
                return false;
            }
        }
        return true;
    }

    private boolean isRerankNoEffect(EvalRunResult result) {
        if (StringUtils.hasText(result.getRerankChangeReason())) {
            return !result.isRerankChanged();
        }
        List<Long> before = result.getTopChunksBeforeRerank();
        List<Long> after = result.getTopChunksAfterRerank();
        if (before == null || before.isEmpty() || after == null || after.isEmpty()) {
            return true;
        }
        return before.equals(after);
    }

    private String rerankDiagnosisReason(String rerankChangeReason) {
        if (!StringUtils.hasText(rerankChangeReason)) {
            return "";
        }
        if ("SINGLE_CANDIDATE".equals(rerankChangeReason)) {
            return "RERANK_SINGLE_CANDIDATE";
        }
        if ("WEAK_SCORING".equals(rerankChangeReason)) {
            return "RERANK_WEAK_SCORING";
        }
        if ("FLAT_SCORES".equals(rerankChangeReason)) {
            return "RERANK_FLAT_SCORES";
        }
        if ("TOP1_CHANGED".equals(rerankChangeReason)) {
            return "RERANK_TOP1_CHANGED";
        }
        if ("ORDER_UNCHANGED".equals(rerankChangeReason)) {
            return "RERANK_ORDER_UNCHANGED";
        }
        if ("CONFIDENT_STABLE".equals(rerankChangeReason)) {
            return "RERANK_CONFIDENT_STABLE";
        }
        return "RERANK_" + rerankChangeReason;
    }

    private String normalizeForDiagnosis(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private EvalRunSummary buildEvalRunSummary(List<EvalRunResult> results) {
        EvalRunSummary summary = new EvalRunSummary();
        int baselineHitCount = 0;
        int graphHitCount = 0;
        int sourceHitCount = 0;
        for (EvalRunResult result : results) {
            if (result.isBaselineHit()) {
                baselineHitCount++;
            }
            if (result.isGraphHit()) {
                graphHitCount++;
            }
            if (result.isSourceHit()) {
                sourceHitCount++;
            }
        }
        summary.setTotal(results.size());
        summary.setBaselineHitCount(baselineHitCount);
        summary.setGraphHitCount(graphHitCount);
        summary.setSourceHitCount(sourceHitCount);
        summary.setBaselineHitRate(ratio(baselineHitCount, results.size()));
        summary.setGraphHitRate(ratio(graphHitCount, results.size()));
        summary.setSourceHitRate(ratio(sourceHitCount, results.size()));
        summary.setResultsPath(LATEST_EVAL_RESULTS_PATH.toString());
        summary.setReportPath(LATEST_EVAL_REPORT_PATH.toString());
        summary.setResults(results);
        return summary;
    }

    private double ratio(int hitCount, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return Double.parseDouble(String.format(Locale.ROOT, "%.4f", (double) hitCount / total));
    }

    private boolean containsSentenceBreak(String text) {
        return text.contains("。") || text.contains("\n") || text.contains("1.") || text.contains("结论");
    }

    private boolean containsAnyIgnoreCase(String text, List<String> terms) {
        if (!StringUtils.hasText(text) || terms == null || terms.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (StringUtils.hasText(term) && lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExpectedKeywords(String text, List<String> expectedKeywords) {
        return containsAnyIgnoreCase(text, expectedKeywords);
    }

    private int countMatchedKeywords(String text, List<String> expectedKeywords) {
        if (!StringUtils.hasText(text) || expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : expectedKeywords) {
            if (StringUtils.hasText(keyword) && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private boolean meetsTableFieldThreshold(int keywordHitCount, List<String> expectedKeywords) {
        int expectedCount = expectedKeywords == null ? 0 : expectedKeywords.size();
        if (expectedCount <= 0) {
            return false;
        }
        double ratio = (double) keywordHitCount / expectedCount;
        return ratio >= 0.7d || keywordHitCount >= 4;
    }

    private boolean matchesExpectedRelations(AskResponse response, List<String> expectedRelations) {
        return containsAnyIgnoreCase(joinStrings(extractRelationSummaries(response)), expectedRelations);
    }

    private boolean matchesSources(AskResponse response, List<String> expectedKeywords) {
        if (response == null || response.getSources() == null) {
            return false;
        }
        for (SourceItem source : response.getSources()) {
            if (source == null) {
                continue;
            }
            if (matchesExpectedKeywords(source.getContent(), expectedKeywords)) {
                return true;
            }
            if (matchesExpectedKeywords(joinStrings(source.getEvidenceSentences()), expectedKeywords)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEvidence(AskResponse response, List<String> expectedKeywords) {
        if (response == null || response.getSources() == null) {
            return false;
        }
        for (SourceItem source : response.getSources()) {
            if (source != null && matchesExpectedKeywords(joinStrings(source.getEvidenceSentences()), expectedKeywords)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExpectedMode(AskResponse graph, String expectedMode) {
        if (graph == null || !StringUtils.hasText(expectedMode)) {
            return false;
        }
        String normalized = expectedMode.trim().toUpperCase(Locale.ROOT);
        String mode = valueOrEmpty(graph.getMode()).toUpperCase(Locale.ROOT);
        String retrievalMode = valueOrEmpty(graph.getRetrievalMode()).toUpperCase(Locale.ROOT);
        if ("GRAPH".equals(normalized)) {
            return "GRAPH".equals(mode)
                    || "AGENT_GRAPH".equals(mode)
                    || "GRAPH".equals(retrievalMode)
                    || "AGENT_GRAPH".equals(retrievalMode);
        }
        return normalized.equals(valueOrEmpty(graph.getMode()).toUpperCase(Locale.ROOT))
                || normalized.equals(valueOrEmpty(graph.getRetrievalMode()).toUpperCase(Locale.ROOT));
    }

    private boolean isTableFieldQuestion(Day6GraphEvalQuestion question) {
        return question != null && "TABLE_FIELDS".equalsIgnoreCase(question.getQuestionType());
    }

    private boolean containsDirtyFieldTerms(String text) {
        return containsAnyIgnoreCase(text, listOf(" ASC", " DESC", "ORDER BY", " PDF ", "searchHybrid", "focusEntity"));
    }

    private String buildDay6Notes(Day6GraphEvalQuestion question,
                                  AskResponse baseline,
                                  AskResponse graph,
                                  Day6GraphEvalResult result,
                                  int baselineKeywordHitCount,
                                  int graphKeywordHitCount) {
        List<String> notes = new ArrayList<>();
        int expectedKeywordCount = question.getExpectedKeywords() == null ? 0 : question.getExpectedKeywords().size();
        if (!result.isBaselineHit() && result.isGraphHit()) {
            notes.add("graph improved hit");
        }
        if (result.isRelationHit()) {
            notes.add("relation hit");
        }
        if (result.isSourceHit()) {
            notes.add("source hit");
        }
        if (result.isEvidenceHit()) {
            notes.add("evidence hit");
        }
        if (!result.isExpectedModeHit()) {
            notes.add("mode mismatch");
        }
        if (isTableFieldQuestion(question) && containsDirtyFieldTerms(graph.getAnswer())) {
            notes.add("dirty field term in graph answer");
        }
        if (!StringUtils.hasText(graph.getAnswer())) {
            notes.add("graph answer empty");
        }
        if (!StringUtils.hasText(baseline.getAnswer())) {
            notes.add("baseline answer empty");
        }
        notes.add("baselineKeywordHitCount=" + baselineKeywordHitCount);
        notes.add("graphKeywordHitCount=" + graphKeywordHitCount);
        notes.add("expectedKeywordCount=" + expectedKeywordCount);
        notes.add("baselineKeywordHitRatio=" + buildRatioText(baselineKeywordHitCount, expectedKeywordCount));
        notes.add("graphKeywordHitRatio=" + buildRatioText(graphKeywordHitCount, expectedKeywordCount));
        return String.join("; ", notes);
    }

    private String buildDay6AgentToolNotes(Day6AgentToolEvalQuestion question,
                                           AskResponse routerOnly,
                                           AskResponse toolAgent,
                                           Day6AgentToolEvalResult result) {
        List<String> notes = new ArrayList<>();
        if (!result.isRouterOnlyHit() && result.isToolAgentHit()) {
            notes.add("tool-agent improved hit");
        }
        if (result.isSqlSearchHit()) {
            notes.add("sql search hit");
        }
        if (result.isTraceHit()) {
            notes.add("trace hit");
        }
        if (!result.isExpectedToolsHit()) {
            notes.add("expected tools mismatch");
        }
        if (!StringUtils.hasText(routerOnly.getAnswer())) {
            notes.add("router-only answer empty");
        }
        if (!StringUtils.hasText(toolAgent.getAnswer())) {
            notes.add("tool-agent answer empty");
        }
        if (StringUtils.hasText(question.getNotes())) {
            notes.add(question.getNotes());
        }
        return String.join("; ", notes);
    }

    private boolean containsAllIgnoreCase(String text, List<String> terms) {
        if (!StringUtils.hasText(text) || terms == null || terms.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!StringUtils.hasText(term) || !lower.contains(term.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private List<String> listOf(String... values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value == null ? "" : value);
        }
        return list;
    }

    private String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> safeValues = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                safeValues.add(value);
            }
        }
        return String.join(" ", safeValues);
    }

    private boolean hasTool(AskResponse response, String toolName) {
        if (response == null || response.getDebug() == null || response.getDebug().getExecutedTools() == null) {
            return false;
        }
        for (ToolType toolType : response.getDebug().getExecutedTools()) {
            if (toolType != null && toolName.equalsIgnoreCase(toolType.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTraceHit(AskResponse response, List<String> expectedTools) {
        if (response == null
                || response.getDebug() == null
                || response.getDebug().getExecutionTrace() == null
                || response.getDebug().getExecutionTrace().getSteps() == null
                || response.getDebug().getExecutionTrace().getSteps().isEmpty()) {
            return false;
        }
        for (AgentExecutionStep step : response.getDebug().getExecutionTrace().getSteps()) {
            if (step == null || step.getToolType() == null || step.getLatencyMs() == null) {
                return false;
            }
        }
        return hasExpectedTools(response, expectedTools);
    }

    private boolean hasExpectedTools(AskResponse response, List<String> expectedTools) {
        if (expectedTools == null || expectedTools.isEmpty()) {
            return true;
        }
        for (String expectedTool : expectedTools) {
            if (!hasTool(response, expectedTool) && !hasTraceTool(response, expectedTool)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasTraceTool(AskResponse response, String toolName) {
        if (response == null
                || response.getDebug() == null
                || response.getDebug().getExecutionTrace() == null
                || response.getDebug().getExecutionTrace().getSteps() == null) {
            return false;
        }
        for (AgentExecutionStep step : response.getDebug().getExecutionTrace().getSteps()) {
            if (step != null && step.getToolType() != null && toolName.equalsIgnoreCase(step.getToolType().name())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSqlSearchHit(AskResponse response) {
        return hasTool(response, "SQL_SEARCH")
                || containsSourceType(response, "SQL_METADATA")
                || containsAnyIgnoreCase(response == null ? "" : response.getAnswer(), listOf("USER_TAB_COLUMNS"));
    }

    private boolean matchesSourceTypes(AskResponse response, List<String> expectedSourceTypes) {
        if (expectedSourceTypes == null || expectedSourceTypes.isEmpty()) {
            return response != null && (response.getSources() == null || response.getSources().isEmpty());
        }
        for (String expectedSourceType : expectedSourceTypes) {
            if (!containsSourceType(response, expectedSourceType)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsSourceType(AskResponse response, String expectedSourceType) {
        if (response == null || response.getSources() == null || !StringUtils.hasText(expectedSourceType)) {
            return false;
        }
        for (SourceItem source : response.getSources()) {
            if (source != null && expectedSourceType.equalsIgnoreCase(source.getSourceType())) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractRelationSummaries(AskResponse response) {
        List<String> values = new ArrayList<>();
        if (response == null || response.getRelationPaths() == null) {
            return values;
        }
        response.getRelationPaths().forEach(item -> {
            if (item != null && StringUtils.hasText(item.getSummary())) {
                values.add(item.getSummary());
            }
        });
        return values;
    }

    private List<String> extractRelationOrSourceTerms(AskResponse response) {
        List<String> values = new ArrayList<>();
        if (response == null) {
            return values;
        }
        if (response.getRelationPaths() != null) {
            response.getRelationPaths().forEach(item -> {
                if (item == null) {
                    return;
                }
                if (StringUtils.hasText(item.getSourceEntity())) {
                    values.add(item.getSourceEntity());
                }
                if (StringUtils.hasText(item.getTargetEntity())) {
                    values.add(item.getTargetEntity());
                }
            });
        }
        if (response.getSources() != null) {
            response.getSources().forEach(item -> {
                if (item != null && StringUtils.hasText(item.getDocumentName())) {
                    values.add(item.getDocumentName());
                }
            });
        }
        return values;
    }

    private void writeEvalRunResults(List<EvalRunResult> results) {
        try {
            Files.createDirectories(LATEST_EVAL_RESULTS_PATH.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(LATEST_EVAL_RESULTS_PATH.toFile(), results);
        } catch (IOException ex) {
            throw new IllegalStateException("write latest eval results failed", ex);
        }
    }

    private void writeEvalRunReport(EvalRunSummary summary) {
        String markdown = buildEvalRunReport(summary);
        try {
            Files.createDirectories(LATEST_EVAL_REPORT_PATH.getParent());
            Files.write(LATEST_EVAL_REPORT_PATH, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("write latest eval report failed", ex);
        }
    }

    private String buildEvalRunReport(EvalRunSummary summary) {
        List<EvalRunResult> results = summary.getResults();
        StringBuilder sb = new StringBuilder();
        sb.append("# Latest RAG Eval Report").append("\n\n");
        sb.append("## Summary").append("\n\n");
        sb.append("- total: ").append(summary.getTotal()).append("\n");
        sb.append("- baselineHitRate: ").append(buildRatioText(summary.getBaselineHitCount(), summary.getTotal()))
                .append(" (").append(summary.getBaselineHitCount()).append("/").append(summary.getTotal()).append(")").append("\n");
        sb.append("- graphHitRate: ").append(buildRatioText(summary.getGraphHitCount(), summary.getTotal()))
                .append(" (").append(summary.getGraphHitCount()).append("/").append(summary.getTotal()).append(")").append("\n");
        sb.append("- sourceHitRate: ").append(buildRatioText(summary.getSourceHitCount(), summary.getTotal()))
                .append(" (").append(summary.getSourceHitCount()).append("/").append(summary.getTotal()).append(")").append("\n\n");

        sb.append("## Baseline Vs Graph").append("\n\n");
        sb.append("| ID | Type | Question | BaselineHit | GraphHit | SourceHit | BaselineReadability | GraphReadability | Noise | Notes |").append("\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |").append("\n");
        for (EvalRunResult result : results) {
            sb.append("| ")
                    .append(md(result.getId())).append(" | ")
                    .append(md(result.getType())).append(" | ")
                    .append(md(result.getQuestion())).append(" | ")
                    .append(result.isBaselineHit() ? "Y" : "N").append(" | ")
                    .append(result.isGraphHit() ? "Y" : "N").append(" | ")
                    .append(result.isSourceHit() ? "Y" : "N").append(" | ")
                    .append(result.getBaselineReadabilityScore()).append(" | ")
                    .append(result.getGraphReadabilityScore()).append(" | ")
                    .append(md(buildEvalNoiseText(result))).append(" | ")
                    .append(md(result.getNotes())).append(" |")
                    .append("\n");
        }

        sb.append("\n## Type Hit Rates").append("\n\n");
        appendTypeHitRates(sb, results);

        sb.append("\n## Diagnosis Reason Summary").append("\n\n");
        appendDiagnosisReasonSummary(sb, results);

        sb.append("\n## Diagnosis Reason Comparison").append("\n\n");
        appendDiagnosisReasonComparison(sb, results);

        sb.append("\n## Query Rewrite Effectiveness").append("\n\n");
        appendQueryRewriteEffectiveness(sb, results);

        sb.append("\n## Recall Effectiveness").append("\n\n");
        appendRecallEffectiveness(sb, results);

        sb.append("\n## Candidate Quality Analysis").append("\n\n");
        appendCandidateQualityAnalysis(sb, results);

        sb.append("\n## Noise Samples").append("\n\n");
        appendEvalSamples(sb, results, "noise", 3);

        sb.append("\n## Rewrite Samples").append("\n\n");
        appendRewriteSamples(sb, results, 5);

        sb.append("\n## Rewrite Effective Samples").append("\n\n");
        appendRewriteEffectiveSamples(sb, results, 5);

        sb.append("\n## Score Details").append("\n\n");
        appendScoreDetailSamples(sb, results, 5);

        sb.append("\n## Rerank Before After").append("\n\n");
        appendRerankSamples(sb, results, 5);

        sb.append("\n## Rerank Diagnostics").append("\n\n");
        appendRerankDiagnostics(sb, results);

        sb.append("\n## Rerank Effective Samples").append("\n\n");
        appendRerankEffectiveSamples(sb, results, 5);

        sb.append("\n## Graph Better Samples").append("\n\n");
        appendEvalSamples(sb, results, "graphBetter", 3);

        sb.append("\n## Graph Worse Samples").append("\n\n");
        appendEvalSamples(sb, results, "graphWorse", 3);

        sb.append("\n## Failure Case Analysis").append("\n\n");
        appendFailureCaseAnalysis(sb, results, 5);

        sb.append("\n## Next Steps").append("\n\n");
        appendEvalNextSteps(sb, results);
        return sb.toString();
    }

    private String buildEvalNoiseText(EvalRunResult result) {
        if (result.isBaselineNoise() && result.isGraphNoise()) {
            return "baseline+graph";
        }
        if (result.isBaselineNoise()) {
            return "baseline";
        }
        if (result.isGraphNoise()) {
            return "graph";
        }
        return "";
    }

    private void appendEvalSamples(StringBuilder sb, List<EvalRunResult> results, String sampleType, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (!matchesEvalSample(result, sampleType)) {
                continue;
            }
            sb.append("- ").append(result.getId()).append(" ")
                    .append(result.getQuestion()).append(" | ")
                    .append(md(result.getNotes())).append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendTypeHitRates(StringBuilder sb, List<EvalRunResult> results) {
        Map<String, TypeEvalStats> statsByType = new LinkedHashMap<>();
        for (EvalRunResult result : results) {
            String type = StringUtils.hasText(result.getType()) ? result.getType() : "UNKNOWN";
            TypeEvalStats stats = statsByType.computeIfAbsent(type, key -> new TypeEvalStats());
            stats.total++;
            if (result.isBaselineHit()) {
                stats.baselineHit++;
            }
            if (result.isGraphHit()) {
                stats.graphHit++;
            }
            if (result.isSourceHit()) {
                stats.sourceHit++;
            }
        }

        sb.append("| Type | Total | BaselineHitRate | GraphHitRate | SourceHitRate |").append("\n");
        sb.append("| --- | --- | --- | --- | --- |").append("\n");
        for (Map.Entry<String, TypeEvalStats> entry : statsByType.entrySet()) {
            TypeEvalStats stats = entry.getValue();
            sb.append("| ")
                    .append(md(entry.getKey())).append(" | ")
                    .append(stats.total).append(" | ")
                    .append(buildRatioText(stats.baselineHit, stats.total)).append(" | ")
                    .append(buildRatioText(stats.graphHit, stats.total)).append(" | ")
                    .append(buildRatioText(stats.sourceHit, stats.total)).append(" |")
                    .append("\n");
        }
    }

    private void appendDiagnosisReasonSummary(StringBuilder sb, List<EvalRunResult> results) {
        Map<String, List<EvalRunResult>> resultsByReason = groupByDiagnosisReason(results);
        if (resultsByReason.isEmpty()) {
            sb.append("- none").append("\n");
            return;
        }
        for (Map.Entry<String, List<EvalRunResult>> entry : resultsByReason.entrySet()) {
            sb.append("### ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append(")").append("\n\n");
            int count = 0;
            for (EvalRunResult result : entry.getValue()) {
                sb.append("- question: ").append(md(result.getQuestion())).append("\n");
                sb.append("  expected: ").append(md(joinStrings(result.getExpectedKeywords()))).append("\n");
                sb.append("  missing: ").append(md(joinStrings(result.getMissingExpectedKeywords()))).append("\n");
                sb.append("  rewrite: ").append(md(valueOrEmpty(result.getRewrittenQuery()))).append("\n");
                sb.append("  rerank: before=").append(result.getTopChunksBeforeRerank())
                        .append(" after=").append(result.getTopChunksAfterRerank()).append("\n");
                sb.append("  reasons: ").append(md(joinStrings(result.getDiagnosisReason()))).append("\n");
                count++;
                if (count >= 3) {
                    break;
                }
            }
            sb.append("\n");
        }
    }

    private Map<String, List<EvalRunResult>> groupByDiagnosisReason(List<EvalRunResult> results) {
        Map<String, List<EvalRunResult>> grouped = new LinkedHashMap<>();
        for (EvalRunResult result : results) {
            if (result.getDiagnosisReason() == null || result.getDiagnosisReason().isEmpty()) {
                grouped.computeIfAbsent("NO_DIAGNOSIS", key -> new ArrayList<>()).add(result);
                continue;
            }
            for (String reason : result.getDiagnosisReason()) {
                grouped.computeIfAbsent(reason, key -> new ArrayList<>()).add(result);
            }
        }
        return grouped;
    }

    private void appendDiagnosisReasonComparison(StringBuilder sb, List<EvalRunResult> results) {
        Map<String, Integer> previous = new LinkedHashMap<>();
        previous.put("REWRITE_NO_EFFECT", 15);
        previous.put("NO_CANDIDATES_RECALLED", 5);
        previous.put("RERANK_NO_EFFECT", 15);
        previous.put("SOURCE_NOT_MATCHED", 6);
        previous.put("ANSWER_MISSING_EXPECTED_KEYWORDS", 13);

        sb.append("| Reason | Previous | Current | Delta |").append("\n");
        sb.append("| --- | --- | --- | --- |").append("\n");
        for (Map.Entry<String, Integer> entry : previous.entrySet()) {
            int current = countDiagnosis(results, entry.getKey());
            sb.append("| ")
                    .append(entry.getKey()).append(" | ")
                    .append(entry.getValue()).append(" | ")
                    .append(current).append(" | ")
                    .append(current - entry.getValue()).append(" |")
                    .append("\n");
        }
    }

    private void appendQueryRewriteEffectiveness(StringBuilder sb, List<EvalRunResult> results) {
        int total = results.size();
        int noEffect = countDiagnosis(results, "REWRITE_NO_EFFECT");
        int effective = Math.max(0, total - noEffect);
        sb.append("- total: ").append(total).append("\n");
        sb.append("- effectiveRewriteCount: ").append(effective).append("\n");
        sb.append("- rewriteNoEffectCount: ").append(noEffect).append("\n");
        sb.append("- effectiveRewriteRate: ").append(buildRatioText(effective, total)).append("\n");
        sb.append("- searchQueryCount: ").append(totalSearchQueryCount(results)).append("\n");
        sb.append("- matchedQueryCount: ").append(totalMatchedQueryCount(results)).append("\n");
    }

    private void appendRecallEffectiveness(StringBuilder sb, List<EvalRunResult> results) {
        int total = results.size();
        int noCandidates = countDiagnosis(results, "NO_CANDIDATES_RECALLED");
        int fallbackCount = 0;
        int mergedCandidates = 0;
        int duplicateRemoved = 0;
        for (EvalRunResult result : results) {
            if (result.isRecallFallbackUsed()) {
                fallbackCount++;
            }
            mergedCandidates += result.getCandidateCountAfterMerge();
            duplicateRemoved += result.getDuplicateCandidateRemovedCount();
        }
        sb.append("- total: ").append(total).append("\n");
        sb.append("- noCandidatesRecalledCount: ").append(noCandidates).append("\n");
        sb.append("- recallSuccessRate: ").append(buildRatioText(total - noCandidates, total)).append("\n");
        sb.append("- fallbackUsedCount: ").append(fallbackCount).append("\n");
        sb.append("- candidateCountAfterMergeTotal: ").append(mergedCandidates).append("\n");
        sb.append("- duplicateCandidateRemovedTotal: ").append(duplicateRemoved).append("\n");
    }

    private void appendCandidateQualityAnalysis(StringBuilder sb, List<EvalRunResult> results) {
        int total = results.size();
        int beforeTotal = 0;
        int uniqueTotal = 0;
        int fallbackCount = 0;
        int duplicateRemoved = 0;
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0", 0);
        distribution.put("1", 0);
        distribution.put("2-4", 0);
        distribution.put("5+", 0);
        Map<String, Integer> rerankReasonCounts = new LinkedHashMap<>();

        for (EvalRunResult result : results) {
            int candidateCount = result.getCandidateCountBeforeRerank();
            beforeTotal += candidateCount;
            uniqueTotal += result.getUniqueCandidateCount();
            duplicateRemoved += result.getDuplicateCandidateRemovedCount();
            if (result.isRecallFallbackUsed()) {
                fallbackCount++;
            }
            increment(distribution, candidateBucket(candidateCount));
            if (StringUtils.hasText(result.getRerankChangeReason())) {
                increment(rerankReasonCounts, result.getRerankChangeReason());
            }
        }

        sb.append("- averageCandidateCountBeforeRerank: ").append(formatDecimal(average(beforeTotal, total))).append("\n");
        sb.append("- averageUniqueCandidateCount: ").append(formatDecimal(average(uniqueTotal, total))).append("\n");
        sb.append("- candidateCountDistribution: ").append(distribution).append("\n");
        sb.append("- rerankChangeReasonCounts: ").append(rerankReasonCounts).append("\n");
        sb.append("- rerankNoEffectMainReason: ").append(md(mainRerankNoEffectReason(rerankReasonCounts))).append("\n");
        sb.append("- recallExpansionEffect: fallbackUsedCount=").append(fallbackCount)
                .append(", duplicateCandidateRemovedTotal=").append(duplicateRemoved)
                .append(", noCandidatesRecalledCount=").append(countDiagnosis(results, "NO_CANDIDATES_RECALLED"))
                .append("\n");
        sb.append("- candidateQualitySuggestion: ")
                .append(md(candidateQualitySuggestion(results, rerankReasonCounts)))
                .append("\n");
    }

    private String candidateBucket(int candidateCount) {
        if (candidateCount <= 0) {
            return "0";
        }
        if (candidateCount == 1) {
            return "1";
        }
        if (candidateCount <= 4) {
            return "2-4";
        }
        return "5+";
    }

    private void increment(Map<String, Integer> values, String key) {
        values.put(key, values.getOrDefault(key, 0) + 1);
    }

    private double average(int value, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return (double) value / total;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String mainRerankNoEffectReason(Map<String, Integer> rerankReasonCounts) {
        String bestReason = "none";
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : rerankReasonCounts.entrySet()) {
            String reason = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if ("TOP1_CHANGED".equals(reason)) {
                continue;
            }
            if (count > bestCount) {
                bestReason = reason;
                bestCount = count;
            }
        }
        return bestReason + "(" + bestCount + ")";
    }

    private String candidateQualitySuggestion(List<EvalRunResult> results, Map<String, Integer> rerankReasonCounts) {
        int noCandidates = countDiagnosis(results, "NO_CANDIDATES_RECALLED");
        int sourceMiss = countDiagnosis(results, "SOURCE_NOT_MATCHED");
        int answerMiss = countDiagnosis(results, "ANSWER_MISSING_EXPECTED_KEYWORDS");
        int single = rerankReasonCounts.getOrDefault("SINGLE_CANDIDATE", 0);
        int weak = rerankReasonCounts.getOrDefault("WEAK_SCORING", 0);
        int flat = rerankReasonCounts.getOrDefault("FLAT_SCORES", 0);
        int orderUnchanged = rerankReasonCounts.getOrDefault("ORDER_UNCHANGED", 0);
        int confidentStable = rerankReasonCounts.getOrDefault("CONFIDENT_STABLE", 0);
        if (confidentStable > Math.max(single, flat + weak + orderUnchanged)) {
            return "排序已经稳定，下一步优先看 evidence/source 和 answer assembly。";
        }
        if (noCandidates > 0 || single > Math.max(orderUnchanged, flat + weak)) {
            return "优先调 recall：空召回或单候选会让 rerank 没有可比较对象。";
        }
        if (orderUnchanged > 0 || weak > 0 || flat > 0) {
            return "优先调 scoring/rerank：候选已经变多，但排序顺序不变或分数过平。";
        }
        if (sourceMiss > 0) {
            return "优先调 evidence/source：答案可能命中但来源证据没有匹配。";
        }
        if (answerMiss > 0) {
            return "优先调 answer：候选存在但预期关键词没有被组织进回答。";
        }
        return "继续保留 eval 作为回归门禁，下一步看单题证据链。";
    }

    private int totalSearchQueryCount(List<EvalRunResult> results) {
        int total = 0;
        for (EvalRunResult result : results) {
            total += result.getSearchQueries() == null ? 0 : result.getSearchQueries().size();
        }
        return total;
    }

    private int totalMatchedQueryCount(List<EvalRunResult> results) {
        int total = 0;
        for (EvalRunResult result : results) {
            total += result.getMatchedQueries() == null ? 0 : result.getMatchedQueries().size();
        }
        return total;
    }

    private void appendRewriteSamples(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (!StringUtils.hasText(result.getGraphRewrittenQuery())) {
                continue;
            }
            sb.append("- ").append(result.getId())
                    .append(" intent=").append(valueOrEmpty(result.getGraphDetectedIntent()))
                    .append(" rewritten=").append(md(result.getGraphRewrittenQuery()))
                    .append(" expanded=").append(md(joinStrings(result.getGraphExpandedKeywords())))
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendRewriteEffectiveSamples(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.getDiagnosisReason() != null && result.getDiagnosisReason().contains("REWRITE_NO_EFFECT")) {
                continue;
            }
            if (!StringUtils.hasText(result.getRewrittenQuery())) {
                continue;
            }
            sb.append("- ").append(result.getId())
                    .append(" rewritten=").append(md(result.getRewrittenQuery()))
                    .append(" expanded=").append(md(joinStrings(result.getExpandedKeywords())))
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendScoreDetailSamples(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.getTopScoreDetails() == null || result.getTopScoreDetails().isEmpty()) {
                continue;
            }
            RetrievalScoreDetail detail = result.getTopScoreDetails().get(0);
            sb.append("- ").append(result.getId())
                    .append(" chunk=").append(detail.getChunkId())
                    .append(" keyword=").append(detail.getKeywordScore())
                    .append(" keywordCoverage=").append(detail.getKeywordCoverage())
                    .append(" entity=").append(detail.getEntityScore())
                    .append(" entityCoverage=").append(detail.getEntityCoverage())
                    .append(" relation=").append(detail.getRelationScore())
                    .append(" intentMatch=").append(detail.getIntentMatch())
                    .append(" source=").append(detail.getSourceScore())
                    .append(" sourceType=").append(md(valueOrEmpty(detail.getSourceType())))
                    .append(" noisePenalty=").append(detail.getNoisePenalty())
                    .append(" final=").append(detail.getFinalScore())
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendRerankSamples(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.getRerankBeforeChunkIds() == null || result.getRerankBeforeChunkIds().isEmpty()) {
                continue;
            }
            sb.append("- ").append(result.getId())
                    .append(" before=").append(result.getRerankBeforeChunkIds())
                    .append(" after=").append(result.getRerankAfterChunkIds())
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendRerankDiagnostics(StringBuilder sb, List<EvalRunResult> results) {
        int orderUnchanged = countDiagnosis(results, "RERANK_ORDER_UNCHANGED");
        int flatScores = countDiagnosis(results, "RERANK_FLAT_SCORES");
        int changed = 0;
        double topScoreGapTotal = 0.0d;
        for (EvalRunResult result : results) {
            if (result.isRerankChanged()) {
                changed++;
            }
            topScoreGapTotal += result.getTopScoreGap();
        }
        sb.append("- orderUnchangedCount: ").append(orderUnchanged).append("\n");
        sb.append("- flatScoresCount: ").append(flatScores).append("\n");
        sb.append("- rerankChangedCount: ").append(changed).append("\n");
        sb.append("- averageTopScoreGap: ").append(formatDecimal(averageDouble(topScoreGapTotal, results.size()))).append("\n");
        sb.append("- sortingChangedCases:").append("\n");
        appendSortingChangedCases(sb, results, 3);
    }

    private void appendSortingChangedCases(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (!result.isRerankChanged()) {
                continue;
            }
            sb.append("  - ").append(result.getId())
                    .append(" before=").append(result.getTopChunksBeforeRerank())
                    .append(" after=").append(result.getTopChunksAfterRerank())
                    .append(" topScoreGap=").append(result.getTopScoreGap())
                    .append(" reason=").append(md(valueOrEmpty(result.getRerankChangeReason())));
            if (result.getTopScoreDetails() != null && !result.getTopScoreDetails().isEmpty()) {
                RetrievalScoreDetail detail = result.getTopScoreDetails().get(0);
                sb.append(" topChunk=").append(detail.getChunkId())
                        .append(" final=").append(detail.getFinalScore())
                        .append(" intent=").append(detail.getIntentMatch())
                        .append(" entityCoverage=").append(detail.getEntityCoverage())
                        .append(" noisePenalty=").append(detail.getNoisePenalty());
            }
            sb.append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("  - none").append("\n");
        }
    }

    private double averageDouble(double value, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return value / total;
    }

    private void appendRerankEffectiveSamples(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.getDiagnosisReason() != null && result.getDiagnosisReason().contains("RERANK_NO_EFFECT")) {
                continue;
            }
            if (result.getTopChunksBeforeRerank() == null || result.getTopChunksBeforeRerank().isEmpty()) {
                continue;
            }
            sb.append("- ").append(result.getId())
                    .append(" before=").append(result.getTopChunksBeforeRerank())
                    .append(" after=").append(result.getTopChunksAfterRerank())
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private void appendFailureCaseAnalysis(StringBuilder sb, List<EvalRunResult> results, int maxCount) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.isGraphHit() && result.isSourceHit() && !result.isGraphNoise()) {
                continue;
            }
            sb.append("- ").append(result.getId()).append(" ")
                    .append(md(result.getQuestion())).append(": ");
            sb.append(md(joinStrings(result.getDiagnosisReason())))
                    .append("; missing=")
                    .append(md(joinStrings(result.getMissingExpectedKeywords())))
                    .append("; rewrite=")
                    .append(md(valueOrEmpty(result.getRewrittenQuery())))
                    .append("; rerank before=")
                    .append(result.getTopChunksBeforeRerank())
                    .append(" after=")
                    .append(result.getTopChunksAfterRerank())
                    .append("\n");
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        if (count == 0) {
            sb.append("- none").append("\n");
        }
    }

    private boolean matchesEvalSample(EvalRunResult result, String sampleType) {
        if ("noise".equals(sampleType)) {
            return result.isBaselineNoise() || result.isGraphNoise();
        }
        if ("graphBetter".equals(sampleType)) {
            return !result.isBaselineHit() && result.isGraphHit();
        }
        if ("graphWorse".equals(sampleType)) {
            return result.isBaselineHit() && !result.isGraphHit();
        }
        return false;
    }

    private void appendEvalNextSteps(StringBuilder sb, List<EvalRunResult> results) {
        int noCandidates = countDiagnosis(results, "NO_CANDIDATES_RECALLED");
        int rewriteNoEffect = countDiagnosis(results, "REWRITE_NO_EFFECT");
        int rerankNoEffect = countDiagnosis(results, "RERANK_NO_EFFECT");
        int rerankSingle = countDiagnosis(results, "RERANK_SINGLE_CANDIDATE");
        int rerankFlat = countDiagnosis(results, "RERANK_FLAT_SCORES");
        int rerankWeak = countDiagnosis(results, "RERANK_WEAK_SCORING");
        int sourceMiss = countDiagnosis(results, "SOURCE_NOT_MATCHED");
        int answerMiss = countDiagnosis(results, "ANSWER_MISSING_EXPECTED_KEYWORDS");

        int rerankOrderUnchanged = countDiagnosis(results, "RERANK_ORDER_UNCHANGED");
        int rerankConfidentStable = countDiagnosis(results, "RERANK_CONFIDENT_STABLE");
        if (rerankConfidentStable > Math.max(rerankSingle, rerankOrderUnchanged + rerankFlat + rerankWeak)) {
            sb.append("- Main priority: inspect evidence/source and answer assembly, because rerank now produces confident stable top candidates.").append("\n");
        } else if (noCandidates > 0 || rerankSingle > Math.max(rerankOrderUnchanged, rerankFlat + rerankWeak)) {
            sb.append("- Main priority: optimize recall depth and candidate diversity, because most rerank-no-effect cases have only one candidate.").append("\n");
        } else if (rerankOrderUnchanged > 0 || rerankFlat > 0 || rerankWeak > 0) {
            sb.append("- Main priority: tune scoring/rerank features, because recall now returns more candidates but ordering often stays unchanged or flat.").append("\n");
        } else if (rerankNoEffect > 0 && rerankSingle == 0) {
            sb.append("- Inspect rerank ordering for unchanged multi-candidate cases.").append("\n");
        }
        if (rewriteNoEffect > 0) {
            sb.append("- Improve rewrite rules for cases where rewrittenQuery only repeats the original terms.").append("\n");
        }
        if (sourceMiss > 0) {
            sb.append("- Improve evidence/source matching so graph answers expose the expected source terms.").append("\n");
        }
        if (answerMiss > 0) {
            sb.append("- Inspect answer assembly for cases where candidates exist but expected keywords are not surfaced.").append("\n");
        }
        sb.append("- Keep the current fixed eval as a regression gate before changing retrieval algorithms.").append("\n");
    }

    private int countDiagnosis(List<EvalRunResult> results, String reason) {
        int count = 0;
        for (EvalRunResult result : results) {
            if (result.getDiagnosisReason() != null && result.getDiagnosisReason().contains(reason)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasEvalSourceMiss(List<EvalRunResult> results) {
        for (EvalRunResult result : results) {
            if (!result.isSourceHit()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEvalGraphRegression(List<EvalRunResult> results) {
        for (EvalRunResult result : results) {
            if (result.isBaselineHit() && !result.isGraphHit()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEvalNoise(List<EvalRunResult> results) {
        for (EvalRunResult result : results) {
            if (result.isBaselineNoise() || result.isGraphNoise()) {
                return true;
            }
        }
        return false;
    }

    private void writeRawResults(List<Day7EvalResult> results) {
        try {
            Files.createDirectories(DAY7_EVAL_RAW_RESULT_PATH.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(DAY7_EVAL_RAW_RESULT_PATH.toFile(), results);
        } catch (IOException ex) {
            throw new IllegalStateException("write day7 raw results failed", ex);
        }
    }

    private void writeDay6RawResults(List<Day6GraphEvalResult> results) {
        try {
            Files.createDirectories(DAY6_EVAL_RAW_RESULT_PATH.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(DAY6_EVAL_RAW_RESULT_PATH.toFile(), results);
        } catch (IOException ex) {
            throw new IllegalStateException("write day6 raw results failed", ex);
        }
    }

    private void writeDay6Markdown(List<Day6GraphEvalResult> results) {
        String markdown = buildDay6Markdown(results);
        try {
            Files.createDirectories(DAY6_EVAL_MARKDOWN_PATH.getParent());
            Files.write(DAY6_EVAL_MARKDOWN_PATH, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("write day6 eval markdown failed", ex);
        }
    }

    private void writeDay6AgentToolRawResults(List<Day6AgentToolEvalResult> results) {
        try {
            Files.createDirectories(DAY6_AGENT_TOOL_EVAL_RAW_RESULT_PATH.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(DAY6_AGENT_TOOL_EVAL_RAW_RESULT_PATH.toFile(), results);
        } catch (IOException ex) {
            throw new IllegalStateException("write day6 agent tool raw results failed", ex);
        }
    }

    private void writeDay6AgentToolMarkdown(List<Day6AgentToolEvalResult> results) {
        String markdown = buildDay6AgentToolMarkdown(results);
        try {
            Files.createDirectories(DAY6_AGENT_TOOL_EVAL_MARKDOWN_PATH.getParent());
            Files.write(DAY6_AGENT_TOOL_EVAL_MARKDOWN_PATH, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("write day6 agent tool eval markdown failed", ex);
        }
    }

    private String buildDay6AgentToolMarkdown(List<Day6AgentToolEvalResult> results) {
        int routerOnlyHitCount = 0;
        int toolAgentHitCount = 0;
        int graphHitCount = 0;
        int sqlSearchHitCount = 0;
        int chunkSearchHitCount = 0;
        int traceHitCount = 0;
        int sourceHitCount = 0;
        int evidenceHitCount = 0;
        int expectedToolsHitCount = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("# Day 6 Agent Tool Eval Report").append("\n\n");
        sb.append("| ID | Question Type | Question | RouterOnlyHit | ToolAgentHit | GraphHit | SqlSearchHit | ChunkSearchHit | TraceHit | SourceHit | EvidenceHit | ExpectedToolsHit | Notes |").append("\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |").append("\n");

        for (Day6AgentToolEvalResult result : results) {
            if (result.isRouterOnlyHit()) {
                routerOnlyHitCount++;
            }
            if (result.isToolAgentHit()) {
                toolAgentHitCount++;
            }
            if (result.isGraphHit()) {
                graphHitCount++;
            }
            if (result.isSqlSearchHit()) {
                sqlSearchHitCount++;
            }
            if (result.isChunkSearchHit()) {
                chunkSearchHitCount++;
            }
            if (result.isTraceHit()) {
                traceHitCount++;
            }
            if (result.isSourceHit()) {
                sourceHitCount++;
            }
            if (result.isEvidenceHit()) {
                evidenceHitCount++;
            }
            if (result.isExpectedToolsHit()) {
                expectedToolsHitCount++;
            }

            sb.append("| ")
                    .append(md(result.getId())).append(" | ")
                    .append(md(result.getQuestionType())).append(" | ")
                    .append(md(result.getQuestion())).append(" | ")
                    .append(result.isRouterOnlyHit() ? "Y" : "N").append(" | ")
                    .append(result.isToolAgentHit() ? "Y" : "N").append(" | ")
                    .append(result.isGraphHit() ? "Y" : "N").append(" | ")
                    .append(result.isSqlSearchHit() ? "Y" : "N").append(" | ")
                    .append(result.isChunkSearchHit() ? "Y" : "N").append(" | ")
                    .append(result.isTraceHit() ? "Y" : "N").append(" | ")
                    .append(result.isSourceHit() ? "Y" : "N").append(" | ")
                    .append(result.isEvidenceHit() ? "Y" : "N").append(" | ")
                    .append(result.isExpectedToolsHit() ? "Y" : "N").append(" | ")
                    .append(md(result.getNotes())).append(" |")
                    .append("\n");
        }

        sb.append("\n## Summary\n\n");
        sb.append("- totalQuestions: ").append(results.size()).append("\n");
        sb.append("- routerOnlyHitCount: ").append(routerOnlyHitCount).append("\n");
        sb.append("- toolAgentHitCount: ").append(toolAgentHitCount).append("\n");
        sb.append("- graphHitCount: ").append(graphHitCount).append("\n");
        sb.append("- sqlSearchHitCount: ").append(sqlSearchHitCount).append("\n");
        sb.append("- chunkSearchHitCount: ").append(chunkSearchHitCount).append("\n");
        sb.append("- traceHitCount: ").append(traceHitCount).append("\n");
        sb.append("- sourceHitCount: ").append(sourceHitCount).append("\n");
        sb.append("- evidenceHitCount: ").append(evidenceHitCount).append("\n");
        sb.append("- expectedToolsHitCount: ").append(expectedToolsHitCount).append("\n");
        return sb.toString();
    }

    private String buildDay6Markdown(List<Day6GraphEvalResult> results) {
        int baselineHitCount = 0;
        int graphHitCount = 0;
        int relationHitCount = 0;
        int sourceHitCount = 0;
        int evidenceHitCount = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("# Day 6 Graph Eval Report").append("\n\n");
        sb.append("| ID | 问题类型 | 问题 | 期望模式 | Baseline命中 | Graph命中 | Relation命中 | Source命中 | Evidence命中 | 备注 |").append("\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |").append("\n");

        for (Day6GraphEvalResult result : results) {
            if (result.isBaselineHit()) {
                baselineHitCount++;
            }
            if (result.isGraphHit()) {
                graphHitCount++;
            }
            if (result.isRelationHit()) {
                relationHitCount++;
            }
            if (result.isSourceHit()) {
                sourceHitCount++;
            }
            if (result.isEvidenceHit()) {
                evidenceHitCount++;
            }

            sb.append("| ")
                    .append(md(result.getId())).append(" | ")
                    .append(md(result.getQuestionType())).append(" | ")
                    .append(md(result.getQuestion())).append(" | ")
                    .append(md(result.getExpectedMode())).append(" | ")
                    .append(result.isBaselineHit() ? "是" : "否").append(" | ")
                    .append(result.isGraphHit() ? "是" : "否").append(" | ")
                    .append(result.isRelationHit() ? "是" : "否").append(" | ")
                    .append(result.isSourceHit() ? "是" : "否").append(" | ")
                    .append(result.isEvidenceHit() ? "是" : "否").append(" | ")
                    .append(md(result.getNotes())).append(" |")
                    .append("\n");
        }

        sb.append("\n## Summary\n\n");
        sb.append("- totalQuestions: ").append(results.size()).append("\n");
        sb.append("- baselineHitCount: ").append(baselineHitCount).append("\n");
        sb.append("- graphHitCount: ").append(graphHitCount).append("\n");
        sb.append("- relationHitCount: ").append(relationHitCount).append("\n");
        sb.append("- sourceHitCount: ").append(sourceHitCount).append("\n");
        sb.append("- evidenceHitCount: ").append(evidenceHitCount).append("\n");
        return sb.toString();
    }

    private void writeEvalMarkdown(List<Day7EvalResult> results) {
        String markdown = buildEvalMarkdown(results);
        try {
            Files.createDirectories(DAY7_EVAL_MARKDOWN_PATH.getParent());
            Files.write(DAY7_EVAL_MARKDOWN_PATH, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("write day7 eval markdown failed", ex);
        }
    }

    private String buildEvalMarkdown(List<Day7EvalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Day 7 Eval Result").append("\n\n");
        sb.append("| 编号 | 分类 | 问题 | 期望答案 | Baseline命中 | Graph版命中 | Baseline可读性 | Graph版可读性 | 噪声情况 | 备注 |").append("\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |").append("\n");

        for (Day7EvalResult result : results) {
            sb.append("| ")
                    .append(md(result.getId())).append(" | ")
                    .append(md(result.getCategory())).append(" | ")
                    .append(md(result.getQuestion())).append(" | ")
                    .append(md(result.getExpectedAnswer())).append(" | ")
                    .append(result.isBaselineHit() ? "是" : "否").append(" | ")
                    .append(result.isGraphHit() ? "是" : "否").append(" | ")
                    .append(result.getBaselineReadabilityScore()).append(" | ")
                    .append(result.getGraphReadabilityScore()).append(" | ")
                    .append(buildNoiseText(result)).append(" | ")
                    .append(md(result.getNotes())).append(" |")
                    .append("\n");
        }

        sb.append("\n");
        sb.append("## 总结").append("\n\n");
        sb.append("1. 哪类题提升最大").append("\n");
        sb.append(summaryLine(results, "归属类", "依赖类", "配置关联类")).append("\n\n");
        sb.append("2. 哪类题提升有限").append("\n");
        sb.append(summaryLine(results, "简单定义类")).append("\n\n");
        sb.append("3. 哪类题还会翻车").append("\n");
        sb.append(summaryLine(results, "异常定位类")).append("\n\n");
        sb.append("4. 当前主要问题").append("\n");
        appendMainIssues(sb, results);
        return sb.toString();
    }

    private String summaryLine(List<Day7EvalResult> results, String... categories) {
        int total = 0;
        int improvedHit = 0;
        int improvedReadability = 0;
        for (Day7EvalResult result : results) {
            if (!matchesCategory(result, categories)) {
                continue;
            }
            total++;
            if (!result.isBaselineHit() && result.isGraphHit()) {
                improvedHit++;
            }
            if (result.getGraphReadabilityScore() > result.getBaselineReadabilityScore()) {
                improvedReadability++;
            }
        }
        return "- 覆盖 " + total + " 题，Graph 相比 Baseline 新增命中 " + improvedHit + " 题，可读性提升 " + improvedReadability + " 题。";
    }

    private boolean matchesCategory(Day7EvalResult result, String... categories) {
        if (result == null || categories == null) {
            return false;
        }
        for (String category : categories) {
            if (category != null && category.equals(result.getCategory())) {
                return true;
            }
        }
        return false;
    }

    private void appendMainIssues(StringBuilder sb, List<Day7EvalResult> results) {
        sb.append("- 同 chunk 共现导致 RELATED_TO 噪声偏大。").append("\n");
        if (hasAnyNoise(results)) {
            sb.append("- TEST_SPEC 需要继续彻底隔离，避免评测说明语料回流到答案和证据。").append("\n");
        }
        sb.append("- relation evidence 仍需 sentence 级优化，尤其是多主题 chunk。").append("\n");
        if (hasAnyCategoryMiss(results, "异常定位类")) {
            sb.append("- 异常定位缺少真实运行日志和因果链路，Graph 版本也容易回答泛化。").append("\n");
        }
    }

    private boolean hasAnyNoise(List<Day7EvalResult> results) {
        for (Day7EvalResult result : results) {
            if (result.isBaselineNoise() || result.isGraphNoise()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyCategoryMiss(List<Day7EvalResult> results, String category) {
        for (Day7EvalResult result : results) {
            if (category.equals(result.getCategory()) && !result.isGraphHit()) {
                return true;
            }
        }
        return false;
    }

    private String buildNoiseText(Day7EvalResult result) {
        if (result.isBaselineNoise() && result.isGraphNoise()) {
            return "Baseline/Graph均有";
        }
        if (result.isBaselineNoise()) {
            return "Baseline有";
        }
        if (result.isGraphNoise()) {
            return "Graph有";
        }
        return "无";
    }

    private String md(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", "<br/>");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String buildRatioText(int hitCount, int expectedCount) {
        if (expectedCount <= 0) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", (double) hitCount / expectedCount);
    }

    private static class TypeEvalStats {
        private int total;
        private int baselineHit;
        private int graphHit;
        private int sourceHit;
    }
}
