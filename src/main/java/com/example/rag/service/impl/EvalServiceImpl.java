package com.example.rag.service.impl;

import com.example.rag.dto.Day6GraphEvalQuestion;
import com.example.rag.dto.Day6GraphEvalResult;
import com.example.rag.dto.Day7EvalQuestion;
import com.example.rag.dto.Day7EvalResult;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.SourceItem;
import com.example.rag.service.AskService;
import com.example.rag.service.EvalService;
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
import java.util.List;
import java.util.Locale;

@Service
public class EvalServiceImpl implements EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalServiceImpl.class);
    private static final String DAY6_EVAL_RESOURCE = "eval/day6_graph_eval_questions.json";
    private static final Path DAY6_EVAL_MARKDOWN_PATH = Paths.get("docs", "eval", "day6_graph_eval_report.md");
    private static final Path DAY6_EVAL_RAW_RESULT_PATH = Paths.get("docs", "eval", "day6_graph_eval_results.json");
    private static final String DAY7_EVAL_RESOURCE = "eval/day7_eval_questions.json";
    private static final Path DAY7_EVAL_MARKDOWN_PATH = Paths.get("docs", "eval", "eval_result.md");
    private static final Path DAY7_EVAL_RAW_RESULT_PATH = Paths.get("docs", "eval", "day7_raw_results.json");

    private final AskService askService;
    private final ObjectMapper objectMapper;

    public EvalServiceImpl(AskService askService, ObjectMapper objectMapper) {
        this.askService = askService;
        this.objectMapper = objectMapper;
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

    private List<Day6GraphEvalQuestion> loadDay6Questions() {
        ClassPathResource resource = new ClassPathResource(DAY6_EVAL_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<Day6GraphEvalQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("load day6 eval questions failed", ex);
        }
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
}
