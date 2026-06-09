package com.example.rag.service.impl;

import com.example.rag.dto.QueryRewriteResult;
import com.example.rag.service.QueryRewriteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("(/?[A-Za-z][A-Za-z0-9_./-]*|RAG_[A-Z0-9_]+)");

    @Override
    public QueryRewriteResult rewrite(String question) {
        String safeQuestion = StringUtils.hasText(question) ? question.trim() : "";
        List<String> detectedEntities = detectEntities(safeQuestion);
        String detectedIntent = detectIntent(safeQuestion, detectedEntities);
        List<String> expandedKeywords = expandKeywords(safeQuestion, detectedIntent);

        QueryRewriteResult result = new QueryRewriteResult();
        result.setOriginalQuestion(safeQuestion);
        result.setExpandedKeywords(expandedKeywords);
        result.setDetectedEntities(detectedEntities);
        result.setDetectedIntent(detectedIntent);
        result.setRewrittenQuery(buildRewrittenQuery(safeQuestion, expandedKeywords, detectedEntities, detectedIntent));
        result.setSearchQueries(buildSearchQueries(safeQuestion, expandedKeywords, detectedEntities, detectedIntent));
        return result;
    }

    private List<String> expandKeywords(String question, String detectedIntent) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addIfContains(question, keywords, "加料", "加料", "投料", "合金", "物料", "加入量");
        addIfContains(question, keywords, "吹炼", "吹炼", "主吹", "重吹", "氧耗", "氧压", "氧枪", "时长", "终点");
        addIfContains(question, keywords, "成分", "成分", "C", "Si", "Mn", "P", "S", "碳硅锰磷硫", "命中", "偏差");
        addIfContains(question, keywords, "温度", "温度", "测温", "终点温度", "出钢温度");
        addIfContains(question, keywords, "异常", "异常", "问题", "偏差", "超限", "波动", "失败");
        addIfContains(question, keywords, "不准", "不准", "误差", "命中率低", "偏差", "失败");
        addIfContains(question, keywords, "source", "source", "依据", "来源", "证据", "文档");
        addIfContains(question, keywords, "来源", "source", "依据", "来源", "证据", "文档");
        addIfContains(question, keywords, "证据", "source", "依据", "来源", "证据", "文档");
        addIntentKeywords(question, detectedIntent, keywords);
        addQuestionTokens(question, keywords);
        return new ArrayList<>(keywords);
    }

    private void addIntentKeywords(String question, String detectedIntent, Set<String> keywords) {
        if ("TABLE_OR_FIELD".equals(detectedIntent)) {
            addAll(keywords, "字段", "列", "COLUMN", "TABLE", "USER_TAB_COLUMNS");
        }
        if ("RELATION".equals(detectedIntent)) {
            addAll(keywords, "关系", "依赖", "DEPENDS_ON", "API_BELONGS_TO_SERVICE", "BELONGS_TO");
        }
        if ("SOURCE".equals(detectedIntent)) {
            addAll(keywords, "source", "sources", "evidence", "依据", "来源", "证据", "文档");
        }
        if ("QUALITY_OR_NOISE".equals(detectedIntent)) {
            addAll(keywords, "噪声", "异常", "问题", "失败", "误差", "命中率低");
        }
        if (StringUtils.hasText(question) && (question.contains("作用") || question.contains("负责"))) {
            addAll(keywords, "作用", "职责", "功能", "负责", "调用链");
        }
        if (StringUtils.hasText(question) && (question.contains("组装") || question.contains("上下文"))) {
            addAll(keywords, "matchedEntities", "matchedRelations", "retrievedChunks", "evidenceTexts", "StructuredContext");
        }
        if (StringUtils.hasText(question) && question.contains("图增强")) {
            addAll(keywords, "graph", "relation", "关系", "实体", "RelationQueryService", "matchedRelations");
        }
    }

    private void addIfContains(String question, Set<String> keywords, String trigger, String... values) {
        if (!StringUtils.hasText(question) || !question.contains(trigger)) {
            return;
        }
        addAll(keywords, values);
    }

    private void addAll(Set<String> keywords, String... values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                keywords.add(value);
            }
        }
    }

    private void addQuestionTokens(String question, Set<String> keywords) {
        Matcher matcher = ENTITY_PATTERN.matcher(StringUtils.hasText(question) ? question : "");
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.hasText(token)) {
                keywords.add(token);
            }
        }
    }

    private List<String> detectEntities(String question) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        Matcher matcher = ENTITY_PATTERN.matcher(StringUtils.hasText(question) ? question : "");
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.hasText(token) && token.length() > 1) {
                entities.add(token);
            }
        }
        return new ArrayList<>(entities);
    }

    private String detectIntent(String question, List<String> detectedEntities) {
        String lower = StringUtils.hasText(question) ? question.toLowerCase(Locale.ROOT) : "";
        if (containsAny(lower, "字段", "表", "column", "table")) {
            return "TABLE_OR_FIELD";
        }
        if (containsAny(lower, "关系", "依赖", "属于", "depends_on", "uses", "belongs_to")) {
            return "RELATION";
        }
        if (containsAny(lower, "来源", "证据", "依据", "source", "evidence")) {
            return "SOURCE";
        }
        if (containsAny(lower, "异常", "不准", "误差", "超限", "不稳定", "噪声")) {
            return "QUALITY_OR_NOISE";
        }
        return detectedEntities == null || detectedEntities.isEmpty() ? "GENERAL" : "ENTITY";
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildRewrittenQuery(String question,
                                       List<String> expandedKeywords,
                                       List<String> detectedEntities,
                                       String detectedIntent) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addIfText(parts, question);
        addIfText(parts, detectedIntent);
        if (expandedKeywords != null) {
            parts.addAll(expandedKeywords);
        }
        if (detectedEntities != null) {
            parts.addAll(detectedEntities);
        }
        return String.join(" ", parts);
    }

    private List<String> buildSearchQueries(String question,
                                            List<String> expandedKeywords,
                                            List<String> detectedEntities,
                                            String detectedIntent) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addIfText(queries, question);
        addIfText(queries, String.join(" ", safeList(expandedKeywords)));
        for (String entity : safeList(detectedEntities)) {
            addIfText(queries, entity + " " + detectedIntent);
            addIfText(queries, entity + " " + String.join(" ", safeList(expandedKeywords)));
        }
        addIfText(queries, detectedIntent + " " + String.join(" ", safeList(expandedKeywords)));
        addIfText(queries, buildRewrittenQuery(question, expandedKeywords, detectedEntities, detectedIntent));
        return new ArrayList<>(queries);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private void addIfText(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }
}
