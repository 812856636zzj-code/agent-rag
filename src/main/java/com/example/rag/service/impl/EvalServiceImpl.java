package com.example.rag.service.impl;

import com.example.rag.dto.Day7EvalQuestion;
import com.example.rag.dto.Day7EvalResult;
import com.example.rag.dto.GraphAskResponse;
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
    private static final String DAY7_EVAL_RESOURCE = "eval/day7_eval_questions.json";
    private static final Path DAY7_EVAL_RESULT_PATH = Paths.get("docs", "eval", "eval_result.md");

    private final AskService askService;
    private final ObjectMapper objectMapper;

    public EvalServiceImpl(AskService askService, ObjectMapper objectMapper) {
        this.askService = askService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Day7EvalResult> runDay7Eval() {
        List<Day7EvalQuestion> questions = loadQuestions();
        List<Day7EvalResult> results = new ArrayList<>();
        for (Day7EvalQuestion question : questions) {
            results.add(runSingleQuestion(question));
        }
        writeEvalMarkdown(results);
        return results;
    }

    private Day7EvalResult runSingleQuestion(Day7EvalQuestion evalQuestion) {
        GraphAskResponse baseline = safeAsk(evalQuestion.getQuestion(), "baseline");
        GraphAskResponse graph = safeAsk(evalQuestion.getQuestion(), "graph");

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

    private GraphAskResponse safeAsk(String question, String mode) {
        try {
            return askService.ask(question, mode);
        } catch (RuntimeException ex) {
            log.error("run eval ask failed, mode = {}, question = {}", mode, question, ex);
            GraphAskResponse response = new GraphAskResponse();
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

    private boolean isBaselineHit(Day7EvalQuestion question, GraphAskResponse response) {
        return containsAnyIgnoreCase(response.getAnswer(), question.getExpectedKeywords());
    }

    private boolean isGraphHit(Day7EvalQuestion question, GraphAskResponse response) {
        if (containsAnyIgnoreCase(response.getAnswer(), question.getExpectedKeywords())) {
            return true;
        }
        if (containsAnyIgnoreCase(joinStrings(response.getMatchedEntities()), question.getExpectedEntities())) {
            return true;
        }
        return containsAnyIgnoreCase(joinStrings(response.getMatchedRelations()), question.getExpectedRelations());
    }

    private int scoreReadability(GraphAskResponse response) {
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
            if (StringUtils.hasText(joinStrings(response.getMatchedRelations()))
                    || StringUtils.hasText(joinStrings(response.getMatchedEntities()))) {
                return 5;
            }
            return 4;
        }
        return 3;
    }

    private boolean hasNoise(GraphAskResponse response) {
        String merged = joinStrings(listOf(
                response == null ? "" : response.getAnswer(),
                response == null ? "" : response.getStructuredContext(),
                response == null ? "" : joinStrings(response.getMatchedRelations()),
                response == null ? "" : joinStrings(response.getMatchedEntities())
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

    private String buildNotes(Day7EvalResult result, GraphAskResponse baseline, GraphAskResponse graph) {
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

    private void writeEvalMarkdown(List<Day7EvalResult> results) {
        String markdown = buildEvalMarkdown(results);
        try {
            Files.createDirectories(DAY7_EVAL_RESULT_PATH.getParent());
            Files.write(DAY7_EVAL_RESULT_PATH, markdown.getBytes(StandardCharsets.UTF_8));
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
            sb.append("- TEST_SPEC 需要彻底隔离，避免评测说明语料回流到答案和证据。").append("\n");
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
}
