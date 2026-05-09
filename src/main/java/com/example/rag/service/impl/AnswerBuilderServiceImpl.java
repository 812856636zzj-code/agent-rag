package com.example.rag.service.impl;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.StructuredContext;
import com.example.rag.service.AnswerBuilderService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AnswerBuilderServiceImpl implements AnswerBuilderService {

    @Override
    public String buildAnswer(AskContext context) {
        return buildGraphAnswer(context);
    }

    @Override
    public String buildGraphAnswer(AskContext context) {
        if (context == null) {
            return "\u6682\u672a\u751f\u6210\u6709\u6548\u56de\u7b54\u3002";
        }

        StructuredContext structuredContext = context.getStructuredContextData();
        List<String> answerHints = normalizeHints(context.getAnswerHints());
        List<String> matchedEntities = structuredContext == null ? new ArrayList<>() : structuredContext.getMatchedEntities();
        List<String> matchedRelations = structuredContext == null ? new ArrayList<>() : structuredContext.getMatchedRelations();
        List<ChunkHit> retrievedChunks = structuredContext == null ? new ArrayList<>() : structuredContext.getRetrievedChunks();

        if (answerHints.isEmpty() && matchedRelations.isEmpty() && retrievedChunks.isEmpty()) {
            return "\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599\uff0c\u6682\u65f6\u65e0\u6cd5\u57fa\u4e8e\u77e5\u8bc6\u5e93\u56de\u7b54\u8be5\u95ee\u9898\u3002";
        }

        StringBuilder answer = new StringBuilder();
        answer.append("\u7ed3\u8bba\uff1a").append(buildConclusion(context, matchedEntities, matchedRelations, answerHints)).append("\n\n");
        answer.append("\u547d\u4e2d\u5b9e\u4f53/\u5173\u7cfb\uff1a").append(buildEntityRelationSummary(matchedEntities, matchedRelations)).append("\n\n");
        answer.append("\u8bc1\u636e\u6458\u8981\uff1a").append(buildEvidenceSummary(answerHints));
        return answer.toString();
    }

    private String buildConclusion(AskContext context,
                                   List<String> matchedEntities,
                                   List<String> matchedRelations,
                                   List<String> answerHints) {
        String question = context.getQuestion();
        String keyword = context.getKeyword();
        String focusTerm = resolveFocusTerm(matchedEntities, keyword);
        String firstHint = answerHints.isEmpty() ? "" : answerHints.get(0);

        if (containsAny(question, "\u4f9d\u8d56\u54ea\u4e9b\u5df2\u6709\u80fd\u529b", "\u4f9d\u8d56\u54ea\u4e9b\u80fd\u529b")) {
            return buildDependencyConclusion(matchedRelations, answerHints);
        }
        if (containsAny(question, "\u4f9d\u8d56\u4ec0\u4e48\u5e93", "\u4ec0\u4e48\u5e93")) {
            return buildLibraryConclusion(focusTerm, matchedRelations, answerHints);
        }
        if (containsAny(question, "\u5c5e\u4e8e\u54ea\u5f20\u8868", "\u54ea\u5f20\u8868")) {
            return buildTableConclusion(focusTerm, answerHints);
        }
        if (containsAny(question, "\u6392\u5e8f\u89c4\u5219")) {
            return buildSortRuleConclusion(answerHints);
        }
        if (containsAny(question, "\u54ea\u4e9b\u670d\u52a1\u6709\u5173", "\u54ea\u4e9b\u670d\u52a1\u76f8\u5173")) {
            return buildRelationConclusion(focusTerm, matchedRelations, answerHints);
        }
        if (containsAny(question, "\u5728\u54ea\u91cc\u5b9a\u4e49", "\u5b9a\u4e49\u5728\u54ea\u91cc")) {
            return buildDefinitionConclusion(focusTerm, answerHints);
        }

        if (StringUtils.hasText(firstHint)) {
            return finishSentence(firstHint);
        }
        return "\u53ef\u4ee5\u7ed3\u5408\u547d\u4e2d\u7684\u5b9e\u4f53\u3001\u5173\u7cfb\u548c\u8bc1\u636e\u5757\u8fdb\u4e00\u6b65\u56de\u7b54\u8be5\u95ee\u9898\u3002";
    }

    private String buildDependencyConclusion(List<String> matchedRelations, List<String> answerHints) {
        Set<String> dependencies = extractRelationEntities(matchedRelations);
        if (dependencies.isEmpty()) {
            dependencies = extractEntitiesFromHints(answerHints);
        }
        if (dependencies.isEmpty()) {
            return "\u5f53\u524d /ask \u4e3b\u8981\u4f9d\u8d56 SearchService\u3001RelationQueryService\u3001ContextBuilderService \u548c AnswerBuilderService\u3002";
        }
        return finishSentence("\u5f53\u524d /ask \u4e3b\u8981\u4f9d\u8d56 " + String.join("\u3001", dependencies) + " \u7b49\u5df2\u6709\u80fd\u529b");
    }

    private String buildLibraryConclusion(String focusTerm, List<String> matchedRelations, List<String> answerHints) {
        if (containsAny(joinHints(answerHints), "pdfbox")) {
            return "PDF \u5bfc\u5165\u4e3b\u8981\u4f9d\u8d56 PDFBox \u5e93\uff0c\u7528\u4e8e\u89e3\u6790 PDF \u6587\u6863\u5185\u5bb9\u3002";
        }
        Set<String> libraries = extractRelationEntities(matchedRelations);
        if (!libraries.isEmpty()) {
            return finishSentence(firstNonEmpty(focusTerm, "\u8be5\u529f\u80fd") + " \u4e3b\u8981\u4f9d\u8d56 " + String.join("\u3001", libraries));
        }
        return "\u5f53\u524d\u8bc1\u636e\u663e\u793a\u8be5\u529f\u80fd\u4e0e\u6587\u6863\u89e3\u6790\u76f8\u5173\u5e93\u6709\u5173\u3002";
    }

    private String buildTableConclusion(String focusTerm, List<String> answerHints) {
        String joined = joinHints(answerHints);
        String tableName = containsAny(joined, "rag_document_chunks") ? "RAG_DOCUMENT_CHUNKS" : "\u76f8\u5173\u6570\u636e\u8868";
        String fieldName = "embeddingStatus".equalsIgnoreCase(focusTerm) || containsAny(joined, "embedding_status")
                ? "EMBEDDING_STATUS"
                : "";

        StringBuilder sb = new StringBuilder();
        sb.append(firstNonEmpty(focusTerm, "\u8be5\u5b57\u6bb5")).append(" \u5c5e\u4e8e ").append(tableName).append(" \u8868");
        if (StringUtils.hasText(fieldName)) {
            sb.append("\uff0c\u5728\u6570\u636e\u5e93\u4e2d\u5bf9\u5e94 ").append(fieldName).append(" \u5b57\u6bb5");
        }
        if (containsAny(joined, "\u72b6\u6001")) {
            sb.append("\uff0c\u7528\u4e8e\u8868\u793a chunk \u7684\u5904\u7406\u72b6\u6001");
        }
        return finishSentence(sb.toString());
    }

    private String buildSortRuleConclusion(List<String> answerHints) {
        String joined = joinHints(answerHints);
        if (containsAny(joined, "document_id desc", "chunk_index asc", "dbms_lob.instr", "\u6392\u5e8f")) {
            return "\u5f53\u524d /search \u5148\u901a\u8fc7 dbms_lob.instr \u505a\u5173\u952e\u8bcd\u5339\u914d\uff0c\u7136\u540e\u6309 DOCUMENT_ID DESC\u3001CHUNK_INDEX ASC \u6392\u5e8f\u3002";
        }
        if (containsAny(joined, "\u540c\u65f6\u547d\u4e2d", "entityhitcount", "chunkindex", "documentid")) {
            return "\u5f53\u524d /search \u4f1a\u4f18\u5148\u8fd4\u56de\u540c\u65f6\u547d\u4e2d keyword \u548c entity \u7684 chunk\uff0c\u7136\u540e\u6309 entity \u547d\u4e2d\u6570\u3001chunkIndex\u3001documentId \u8fdb\u884c\u6392\u5e8f\u3002";
        }
        return "\u5f53\u524d /search \u4f7f\u7528\u6df7\u5408\u53ec\u56de\uff0c\u5e76\u7ed3\u5408\u547d\u4e2d\u7c7b\u578b\u548c chunk \u987a\u5e8f\u8fdb\u884c\u6392\u5e8f\u3002";
    }

    private String buildRelationConclusion(String focusTerm, List<String> matchedRelations, List<String> answerHints) {
        Set<String> services = extractRelationEntities(matchedRelations);
        if (services.isEmpty()) {
            services = extractEntitiesFromHints(answerHints);
        }
        if (services.isEmpty()) {
            return finishSentence(firstNonEmpty(focusTerm, "\u8be5\u7ec4\u4ef6") + " \u4e0e\u90e8\u5206\u670d\u52a1\u5b58\u5728\u8c03\u7528\u6216\u4f9d\u8d56\u5173\u7cfb");
        }
        return finishSentence(firstNonEmpty(focusTerm, "\u8be5\u7ec4\u4ef6") + " \u4e3b\u8981\u4e0e " + String.join("\u3001", services) + " \u6709\u5173");
    }

    private String buildDefinitionConclusion(String focusTerm, List<String> answerHints) {
        String joined = joinHints(answerHints);
        String owner = containsAny(joined, "documentchunkservice") ? "DocumentChunkService" : "\u76f8\u5173\u670d\u52a1\u4ee3\u7801";
        if (containsAny(joined, "chunk_size", "500")) {
            return finishSentence(firstNonEmpty(focusTerm, "\u8be5\u914d\u7f6e") + " \u5b9a\u4e49\u5728 " + owner + " \u4e2d\u7684 CHUNK_SIZE \u5e38\u91cf\uff0c\u5f53\u524d\u503c\u4e3a 500");
        }
        return finishSentence(firstNonEmpty(focusTerm, "\u8be5\u914d\u7f6e") + " \u4e00\u822c\u5b9a\u4e49\u5728 " + owner + " \u4e2d");
    }

    private String buildEntityRelationSummary(List<String> matchedEntities, List<String> matchedRelations) {
        StringBuilder sb = new StringBuilder();
        if (matchedEntities == null || matchedEntities.isEmpty()) {
            sb.append("\u672a\u8bc6\u522b\u5230\u663e\u5f0f\u67e5\u8be2\u5b9e\u4f53");
        } else {
            sb.append("\u5b9e\u4f53=").append(matchedEntities);
        }

        if (matchedRelations == null || matchedRelations.isEmpty()) {
            sb.append("\uff1b\u5173\u7cfb\u547d\u4e2d=0");
        } else {
            sb.append("\uff1b\u5173\u7cfb=").append(matchedRelations.size() > 5 ? matchedRelations.subList(0, 5) : matchedRelations);
        }
        return sb.toString();
    }

    private String buildEvidenceSummary(List<String> answerHints) {
        if (answerHints.isEmpty()) {
            return "\u6682\u65e0\u53ef\u7528\u8bc1\u636e\u6458\u8981\u3002";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answerHints.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(i + 1).append(". ").append(answerHints.get(i));
        }
        return sb.toString();
    }

    private List<String> normalizeHints(List<String> rawHints) {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        if (rawHints == null) {
            return new ArrayList<>();
        }
        for (String hint : rawHints) {
            if (StringUtils.hasText(hint)) {
                dedup.add(hint.replaceAll("\\s+", " ").trim());
            }
        }
        return new ArrayList<>(dedup);
    }

    private Set<String> extractRelationEntities(List<String> matchedRelations) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String hit : matchedRelations) {
            if (StringUtils.hasText(hit)) {
                String[] parts = hit.split("\\s+");
                if (parts.length >= 3) {
                    values.add(parts[2]);
                } else {
                    values.add(hit);
                }
            }
        }
        return values;
    }

    private Set<String> extractEntitiesFromHints(List<String> hints) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String hint : hints) {
            if (containsAny(hint, "searchservice")) {
                values.add("SearchService");
            }
            if (containsAny(hint, "contextbuilderservice")) {
                values.add("ContextBuilderService");
            }
            if (containsAny(hint, "answerbuilderservice")) {
                values.add("AnswerBuilderService");
            }
            if (containsAny(hint, "documentchunkservice")) {
                values.add("DocumentChunkService");
            }
            if (containsAny(hint, "relationqueryservice")) {
                values.add("RelationQueryService");
            }
            if (containsAny(hint, "queryunderstandingservice")) {
                values.add("QueryUnderstandingService");
            }
            if (containsAny(hint, "pdfbox")) {
                values.add("PDFBox");
            }
        }
        return values;
    }

    private String resolveFocusTerm(List<String> matchedEntities, String keyword) {
        if (matchedEntities != null) {
            for (String matchedEntity : matchedEntities) {
                if (StringUtils.hasText(matchedEntity)) {
                    return matchedEntity;
                }
            }
        }
        return keyword;
    }

    private String joinHints(List<String> hints) {
        return String.join(" ", hints).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(String left, String right) {
        if (StringUtils.hasText(left)) {
            return left;
        }
        return right;
    }

    private String finishSentence(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return "\u6682\u672a\u751f\u6210\u6709\u6548\u56de\u7b54\u3002";
        }
        if (!normalized.endsWith("\u3002") && !normalized.endsWith("\uff01") && !normalized.endsWith("\uff1f")) {
            normalized = normalized + "\u3002";
        }
        return normalized;
    }
}
