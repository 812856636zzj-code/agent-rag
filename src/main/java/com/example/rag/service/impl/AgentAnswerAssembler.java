package com.example.rag.service.impl;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.SqlTableColumnResult;
import com.example.rag.dto.SourceItem;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.service.AnswerBuilderService;
import com.example.rag.service.ContextBuilderService;
import com.example.rag.service.EvidenceSelector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AgentAnswerAssembler {

    private static final String NO_ANSWER = "\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599\uff0c\u6682\u65f6\u65e0\u6cd5\u57fa\u4e8e\u77e5\u8bc6\u5e93\u56de\u7b54\u8be5\u95ee\u9898\u3002";

    private final ContextBuilderService contextBuilderService;
    private final AnswerBuilderService answerBuilderService;
    private final EvidenceSelector evidenceSelector;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;

    public AgentAnswerAssembler(ContextBuilderService contextBuilderService,
                                AnswerBuilderService answerBuilderService,
                                EvidenceSelector evidenceSelector,
                                DocumentChunkRepository documentChunkRepository,
                                DocumentRepository documentRepository) {
        this.contextBuilderService = contextBuilderService;
        this.answerBuilderService = answerBuilderService;
        this.evidenceSelector = evidenceSelector;
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
    }

    public AskResponse buildNoAnswerResponse(String question, String mode, String retrievalType) {
        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode(mode);
        response.setKeyword("");
        response.setChunks(new ArrayList<>());
        response.setSources(new ArrayList<>());
        response.setRelationPaths(new ArrayList<>());
        response.setStructuredContext("");
        response.setAnswer(NO_ANSWER);
        response.setKeywordHitCount(0);
        response.setEntityHitCount(0);
        response.setMergedChunkCount(0);
        response.setRetrievalMode(retrievalType);
        response.setDebug(buildDebugInfo(retrievalType, 0, 0, 0));
        return response;
    }

    public AskResponse buildChunkResponse(String question, List<ChunkHit> chunkHits) {
        List<ChunkHit> safeChunkHits = safeChunks(chunkHits);
        if (safeChunkHits.isEmpty()) {
            return buildNoAnswerResponse(question, "BASELINE", "BASELINE");
        }

        AskContext askContext = contextBuilderService.buildContext(question, "", safeChunkHits);
        List<ChunkHit> responseChunks = buildResponseChunks(askContext.getTopChunks(), "");

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("BASELINE");
        response.setKeyword("");
        response.setChunks(responseChunks);
        response.setSources(buildSources(question, askContext.getTopChunks()));
        response.setRelationPaths(new ArrayList<>());
        response.setStructuredContext(askContext.getStructuredContext());
        response.setAnswer(answerBuilderService.buildAnswer(askContext));
        response.setKeywordHitCount(0);
        response.setEntityHitCount(0);
        response.setMergedChunkCount(safeChunkHits.size());
        response.setRetrievalMode("BASELINE");
        response.setDebug(buildDebugInfo("BASELINE", safeChunkHits.size(), 0, responseChunks.size()));
        return response;
    }

    public AskResponse buildRelationResponse(String question, List<RelationHit> relationHits) {
        List<RelationHit> safeRelationHits = relationHits == null ? new ArrayList<>() : relationHits;
        if (safeRelationHits.isEmpty()) {
            return buildNoAnswerResponse(question, "AGENT_GRAPH", "AGENT_GRAPH");
        }

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("AGENT_GRAPH");
        response.setKeyword("");
        response.setChunks(new ArrayList<>());
        response.setSources(buildSourcesFromRelationHits(question, safeRelationHits));
        response.setRelationPaths(buildRelationPathsFromHits(safeRelationHits));
        response.setStructuredContext(buildRelationStructuredContext(question, safeRelationHits));
        response.setAnswer(buildRelationAnswer(question, safeRelationHits));
        response.setKeywordHitCount(0);
        response.setEntityHitCount(0);
        response.setMergedChunkCount(0);
        response.setRetrievalMode("AGENT_GRAPH");
        response.setDebug(buildDebugInfo("AGENT_GRAPH", safeRelationHits.size(), safeRelationHits.size(), 0));
        return response;
    }

    public AskResponse buildSqlMetadataResponse(String question, SqlTableColumnResult sqlResult) {
        if (sqlResult == null || !StringUtils.hasText(sqlResult.getTableName())
                || sqlResult.getColumns() == null || sqlResult.getColumns().isEmpty()) {
            return buildNoAnswerResponse(question, "AGENT_GRAPH", "AGENT_GRAPH");
        }

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setMode("AGENT_GRAPH");
        response.setKeyword(sqlResult.getTableName());
        response.setChunks(new ArrayList<>());
        response.setSources(buildSqlMetadataSources(sqlResult));
        response.setRelationPaths(new ArrayList<>());
        response.setStructuredContext(buildSqlStructuredContext(sqlResult));
        response.setAnswer(buildSqlAnswer(sqlResult));
        response.setKeywordHitCount(0);
        response.setEntityHitCount(0);
        response.setMergedChunkCount(0);
        response.setRetrievalMode("AGENT_GRAPH");
        response.setDebug(buildDebugInfo("AGENT_GRAPH", sqlResult.getColumns().size(), 0, 0));
        return response;
    }

    private List<ChunkHit> safeChunks(List<ChunkHit> chunkHits) {
        return chunkHits == null ? new ArrayList<>() : chunkHits;
    }

    private AskDebugInfo buildDebugInfo(String retrievalType, int hitCount, int relationHitCount, int chunkHitCount) {
        AskDebugInfo debugInfo = new AskDebugInfo();
        debugInfo.setRetrievalType(retrievalType);
        debugInfo.setHitCount(hitCount);
        debugInfo.setRelationHitCount(relationHitCount);
        debugInfo.setChunkHitCount(chunkHitCount);
        debugInfo.setKeywordHitCount(0);
        debugInfo.setEntityHitCount(0);
        return debugInfo;
    }

    private List<ChunkHit> buildResponseChunks(List<ChunkHit> hits, String keyword) {
        List<ChunkHit> chunks = new ArrayList<>();
        if (hits == null || hits.isEmpty()) {
            return chunks;
        }

        for (ChunkHit hit : hits) {
            ChunkHit chunk = new ChunkHit();
            chunk.setChunkId(hit.getChunkId());
            chunk.setDocumentId(hit.getDocumentId());
            chunk.setChunkIndex(hit.getChunkIndex());
            chunk.setTokenCount(hit.getTokenCount());
            chunk.setContent(buildSnippet(hit.getContent(), keyword));
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<SourceItem> buildSources(String question, List<ChunkHit> hits) {
        List<SourceItem> sources = new ArrayList<>();
        if (hits == null || hits.isEmpty()) {
            return sources;
        }

        for (ChunkHit hit : hits) {
            SourceItem source = new SourceItem();
            source.setDocumentId(hit.getDocumentId());
            source.setChunkId(hit.getChunkId());
            source.setDocumentName(resolveDocumentName(hit.getDocumentId()));
            source.setContent(hit.getContent());
            source.setScore(hit.getChunkIndex() == null ? null : Math.max(0.0d, 100.0d - hit.getChunkIndex()));
            source.setSourceType("CHUNK");
            source.setEvidenceSentences(evidenceSelector.selectEvidenceSentences(question, hit.getContent()));
            sources.add(source);
        }
        return sources;
    }

    private List<SourceItem> buildSourcesFromRelationHits(String question, List<RelationHit> relationHits) {
        List<SourceItem> sources = new ArrayList<>();
        if (relationHits == null || relationHits.isEmpty()) {
            return sources;
        }
        for (RelationHit hit : relationHits) {
            String sourceContent = resolveRelationSourceContent(hit);
            SourceItem source = new SourceItem();
            source.setDocumentId(hit.getDocumentId());
            source.setChunkId(hit.getChunkId());
            source.setDocumentName(resolveDocumentName(hit.getDocumentId()));
            source.setContent(sourceContent);
            source.setScore(hit.getConfidence());
            source.setSourceType("RELATION");
            source.setEvidenceSentences(evidenceSelector.selectEvidenceSentences(question, sourceContent));
            sources.add(source);
        }
        return sources;
    }

    private List<RelationPathItem> buildRelationPathsFromHits(List<RelationHit> relationHits) {
        List<RelationPathItem> relationPaths = new ArrayList<>();
        if (relationHits == null || relationHits.isEmpty()) {
            return relationPaths;
        }
        for (RelationHit hit : relationHits) {
            RelationPathItem item = new RelationPathItem();
            item.setSourceEntity(hit.getSourceEntityName());
            item.setSourceEntityType(hit.getSourceEntityType());
            item.setRelationType(hit.getRelationType());
            item.setTargetEntity(hit.getTargetEntityName());
            item.setTargetEntityType(hit.getTargetEntityType());
            item.setEvidenceText(hit.getEvidenceText());
            item.setDocumentId(hit.getDocumentId());
            item.setChunkId(hit.getChunkId());
            item.setConfidence(hit.getConfidence());
            item.setSummary(buildRelationSummary(hit));
            relationPaths.add(item);
        }
        return relationPaths;
    }

    private List<SourceItem> buildSqlMetadataSources(SqlTableColumnResult sqlResult) {
        List<SourceItem> sources = new ArrayList<>();
        SourceItem source = new SourceItem();
        source.setDocumentId(null);
        source.setChunkId(null);
        source.setDocumentName("USER_TAB_COLUMNS");
        source.setContent(buildSqlMetadataContent(sqlResult));
        source.setScore(100.0d);
        source.setSourceType("SQL_METADATA");
        List<String> evidenceSentences = new ArrayList<>();
        evidenceSentences.add(buildSqlMetadataContent(sqlResult));
        source.setEvidenceSentences(evidenceSentences);
        sources.add(source);
        return sources;
    }

    private String buildSqlStructuredContext(SqlTableColumnResult sqlResult) {
        return sqlResult.getTableName() + " columns=" + sqlResult.getColumns();
    }

    private String buildSqlAnswer(SqlTableColumnResult sqlResult) {
        return sqlResult.getTableName()
                + " 表包含以下字段："
                + String.join("、", sqlResult.getColumns())
                + "。";
    }

    private String buildSqlMetadataContent(SqlTableColumnResult sqlResult) {
        return "USER_TAB_COLUMNS: " + sqlResult.getTableName() + " -> " + String.join("、", sqlResult.getColumns());
    }

    private String buildRelationStructuredContext(String question, List<RelationHit> relationHits) {
        if (isTableHasFieldQuestion(relationHits)) {
            return buildTableHasFieldStructuredContext(question, relationHits);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("relationHits=").append(relationHits == null ? 0 : relationHits.size());
        if (relationHits != null) {
            int limit = Math.min(5, relationHits.size());
            for (int i = 0; i < limit; i++) {
                builder.append("\n").append(i + 1).append(". ").append(buildRelationSummary(relationHits.get(i)));
            }
        }
        return builder.toString();
    }

    private String buildRelationAnswer(String question, List<RelationHit> relationHits) {
        if (relationHits == null || relationHits.isEmpty()) {
            return NO_ANSWER;
        }
        if (isTableHasFieldQuestion(relationHits)) {
            return buildTableHasFieldAnswer(question, relationHits);
        }
        RelationHit first = relationHits.get(0);
        StringBuilder builder = new StringBuilder();
        builder.append("结论：").append(buildRelationSummary(first)).append("。");
        builder.append("\n\n命中关系：");
        int limit = Math.min(3, relationHits.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(buildRelationSummary(relationHits.get(i)));
        }
        builder.append("\n\n证据：");
        builder.append(StringUtils.hasText(first.getEvidenceText()) ? first.getEvidenceText() : "暂无证据文本。");
        return builder.toString();
    }

    private boolean isTableHasFieldQuestion(List<RelationHit> relationHits) {
        return relationHits != null
                && !relationHits.isEmpty()
                && "TABLE_HAS_FIELD".equalsIgnoreCase(relationHits.get(0).getRelationType());
    }

    private String buildTableHasFieldAnswer(String question, List<RelationHit> relationHits) {
        RelationHit first = relationHits.get(0);
        String tableName = valueOrEmpty(first.getSourceEntityName());
        List<String> fields = extractTableFields(relationHits, tableName);

        StringBuilder builder = new StringBuilder();
        builder.append("结论：")
                .append(tableName)
                .append(" 表包含以下字段：")
                .append(String.join("、", fields))
                .append("。");

        builder.append("\n\n命中实体/关系：")
                .append(tableName)
                .append(" 的 TABLE_HAS_FIELD 关系命中 ")
                .append(fields.size())
                .append(" 条：")
                .append(String.join("、", fields))
                .append("。");

        builder.append("\n\n证据摘要：");
        List<String> evidences = extractUniqueEvidenceTexts(question, relationHits, 3);
        if (evidences.isEmpty()) {
            builder.append("暂无可用证据摘要。");
        } else {
            for (int i = 0; i < evidences.size(); i++) {
                if (i > 0) {
                    builder.append("\n");
                }
                builder.append(i + 1).append(". ").append(evidences.get(i));
            }
        }
        return builder.toString();
    }

    private String buildTableHasFieldStructuredContext(String question, List<RelationHit> relationHits) {
        RelationHit first = relationHits.get(0);
        String tableName = valueOrEmpty(first.getSourceEntityName());
        List<String> fields = extractTableFields(relationHits, tableName);
        StringBuilder builder = new StringBuilder();
        builder.append("relationHits=").append(relationHits.size());
        builder.append("\n").append(tableName).append(" fields=").append(fields);
        List<String> evidences = extractUniqueEvidenceTexts(question, relationHits, 3);
        for (int i = 0; i < evidences.size(); i++) {
            builder.append("\n").append(i + 1).append(". ").append(evidences.get(i));
        }
        return builder.toString();
    }

    private List<String> extractTableFields(List<RelationHit> relationHits, String tableName) {
        Set<String> fields = new LinkedHashSet<>();
        for (RelationHit hit : relationHits) {
            if (hit == null || !"TABLE_HAS_FIELD".equalsIgnoreCase(hit.getRelationType())) {
                continue;
            }
            if (!valueOrEmpty(tableName).equalsIgnoreCase(valueOrEmpty(hit.getSourceEntityName()))) {
                continue;
            }
            if (StringUtils.hasText(hit.getTargetEntityName())) {
                fields.add(hit.getTargetEntityName());
            }
        }
        return new ArrayList<>(fields);
    }

    private List<String> extractUniqueEvidenceTexts(String question, List<RelationHit> relationHits, int maxCount) {
        Set<String> evidences = new LinkedHashSet<>();
        boolean tableFieldQuestion = isTableFieldEvidenceQuestion(question);
        for (RelationHit hit : relationHits) {
            if (hit != null) {
                String sourceText = resolveRelationSourceContent(hit);
                List<String> sentences = evidenceSelector.selectEvidenceSentences(question, sourceText);
                if (!sentences.isEmpty()) {
                    evidences.addAll(sentences);
                } else if (!tableFieldQuestion && StringUtils.hasText(sourceText)) {
                    evidences.add(sourceText.trim());
                }
            }
            if (evidences.size() >= maxCount) {
                break;
            }
        }
        return new ArrayList<>(evidences);
    }

    private boolean isTableFieldEvidenceQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        return lower.contains("表有哪些字段")
                || lower.contains("有哪些字段")
                || lower.contains("字段包括什么");
    }

    private String resolveRelationSourceContent(RelationHit hit) {
        if (hit == null) {
            return "";
        }
        if (StringUtils.hasText(hit.getEvidenceText())) {
            return hit.getEvidenceText();
        }
        if (hit.getChunkId() != null) {
            return documentChunkRepository.findById(hit.getChunkId())
                    .map(DocumentChunk::getContent)
                    .orElse("");
        }
        return "";
    }

    private String buildRelationSummary(RelationHit hit) {
        if (hit == null) {
            return "";
        }
        return valueOrEmpty(hit.getSourceEntityName()) + " "
                + valueOrEmpty(hit.getRelationType()) + " "
                + valueOrEmpty(hit.getTargetEntityName());
    }

    private String resolveDocumentName(Long documentId) {
        if (documentId == null) {
            return "";
        }
        return documentRepository.findById(documentId)
                .map(Document::getFileName)
                .orElse("");
    }

    private String buildSnippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String trimmed = content.trim();
        if (!StringUtils.hasText(keyword)) {
            return buildSummary(trimmed, 120);
        }

        int index = trimmed.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return buildSummary(trimmed, 120);
        }

        int start = Math.max(0, index - 35);
        int end = Math.min(trimmed.length(), index + keyword.length() + 35);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < trimmed.length() ? "..." : "";
        return prefix
                + trimmed.substring(start, index)
                + "[[" + trimmed.substring(index, Math.min(index + keyword.length(), trimmed.length())) + "]]"
                + trimmed.substring(Math.min(index + keyword.length(), trimmed.length()), end)
                + suffix;
    }

    private String buildSummary(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
