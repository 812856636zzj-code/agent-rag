package com.example.rag.service.impl;

import com.example.rag.dto.QueryEntity;
import com.example.rag.service.QueryEntityExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryEntityExtractionServiceImpl implements QueryEntityExtractionService {

    private static final Pattern PATH_PATTERN = Pattern.compile("/[A-Za-z0-9_\\-/{}/.]+");
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\bRAG_[A-Z0-9_]+\\b");

    private static final String TYPE_LIBRARY = "LIBRARY";
    private static final String TYPE_METHOD = "METHOD";
    private static final String TYPE_FIELD = "FIELD";
    private static final String TYPE_SERVICE = "SERVICE";
    private static final String TYPE_CLASS = "CLASS";
    private static final String TYPE_API = "API";
    private static final String TYPE_TABLE = "TABLE";

    @Override
    public List<QueryEntity> extractQueryEntities(String question) {
        List<QueryEntity> result = new ArrayList<>();
        if (!StringUtils.hasText(question)) {
            return result;
        }

        String text = question.trim();
        Map<String, QueryEntity> dedup = new LinkedHashMap<>();

        collectDirectTerms(dedup, text);
        collectPaths(dedup, text);
        collectTables(dedup, text);
        collectChineseMappings(dedup, text);

        result.addAll(dedup.values());
        return result;
    }

    private void collectDirectTerms(Map<String, QueryEntity> dedup, String question) {
        addLiteral(dedup, question, "PDF", "pdf", null);
        addLiteral(dedup, question, "PDFBox", "pdfbox", TYPE_LIBRARY);
        addLiteral(dedup, question, "Oracle", "oracle", TYPE_LIBRARY);
        addLiteral(dedup, question, "Spring Boot", "spring boot", TYPE_LIBRARY);
        addLiteral(dedup, question, "Lombok", "lombok", TYPE_LIBRARY);
        addLiteral(dedup, question, "dbms_lob.instr", "dbms_lob.instr", TYPE_METHOD);
        addLiteral(dedup, question, "embeddingStatus", "embeddingstatus", TYPE_FIELD);
        addLiteral(dedup, question, "documentId", "documentid", TYPE_FIELD);
        addLiteral(dedup, question, "chunkIndex", "chunkindex", TYPE_FIELD);
        addLiteral(dedup, question, "chunkContent", "chunkcontent", TYPE_FIELD);
        addLiteral(dedup, question, "SearchService", "searchservice", TYPE_SERVICE);
        addLiteral(dedup, question, "ContextBuilderService", "contextbuilderservice", TYPE_SERVICE);
        addLiteral(dedup, question, "EntityExtractionService", "entityextractionservice", TYPE_SERVICE);
        addLiteral(dedup, question, "AnswerBuilderService", "answerbuilderservice", TYPE_SERVICE);
        addLiteral(dedup, question, "AskContext", "askcontext", TYPE_CLASS);
        addLiteral(dedup, question, "ChunkHit", "chunkhit", TYPE_CLASS);
        addLiteral(dedup, question, "SearchResult", "searchresult", TYPE_CLASS);
        addLiteral(dedup, question, "chunk", "chunk", null);
    }

    private void collectPaths(Map<String, QueryEntity> dedup, String question) {
        Matcher matcher = PATH_PATTERN.matcher(question);
        while (matcher.find()) {
            String path = matcher.group();
            addEntity(dedup, path, path.toLowerCase(Locale.ROOT), TYPE_API, 1.0d);
        }
    }

    private void collectTables(Map<String, QueryEntity> dedup, String question) {
        Matcher matcher = TABLE_PATTERN.matcher(question);
        while (matcher.find()) {
            String tableName = matcher.group();
            addEntity(dedup, tableName, tableName.toLowerCase(Locale.ROOT), TYPE_TABLE, 1.0d);
        }
    }

    private void collectChineseMappings(Map<String, QueryEntity> dedup, String question) {
        addChineseKeyword(dedup, question, "库", "library");
        addChineseKeyword(dedup, question, "依赖", "dependency");
        addChineseKeyword(dedup, question, "导入", "upload");
        addChineseKeyword(dedup, question, "上传", "upload");
        addChineseKeyword(dedup, question, "表", "table");
        addChineseKeyword(dedup, question, "字段", "field");
        addChineseKeyword(dedup, question, "状态", "status");
        addChineseKeyword(dedup, question, "大小", "size");
        addChineseKeyword(dedup, question, "定义", "define");
        addChineseKeyword(dedup, question, "分块", "chunk");
    }

    private void addLiteral(Map<String, QueryEntity> dedup,
                            String question,
                            String literal,
                            String normalized,
                            String entityType) {
        if (question.contains(literal)) {
            addEntity(dedup, literal, normalized, entityType, 1.0d);
        }
    }

    private void addChineseKeyword(Map<String, QueryEntity> dedup,
                                   String question,
                                   String text,
                                   String normalized) {
        if (question.contains(text)) {
            addEntity(dedup, text, normalized, null, 1.0d);
        }
    }

    private void addEntity(Map<String, QueryEntity> dedup,
                           String text,
                           String normalizedText,
                           String entityType,
                           double weight) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(normalizedText)) {
            return;
        }

        String key = normalizedText.toLowerCase(Locale.ROOT);
        if (dedup.containsKey(key)) {
            return;
        }

        QueryEntity entity = new QueryEntity();
        entity.setText(text);
        entity.setNormalizedText(key);
        entity.setEntityType(entityType);
        entity.setWeight(weight);
        dedup.put(key, entity);
    }
}
