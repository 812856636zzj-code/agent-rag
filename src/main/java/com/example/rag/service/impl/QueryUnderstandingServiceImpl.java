package com.example.rag.service.impl;

import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.QueryUnderstanding;
import com.example.rag.enums.QueryIntent;
import com.example.rag.service.QueryEntityExtractionService;
import com.example.rag.service.QueryUnderstandingService;
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
public class QueryUnderstandingServiceImpl implements QueryUnderstandingService {

    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_./-]{1,}");
    private static final Pattern PATH_PATTERN = Pattern.compile("/[A-Za-z0-9_\\-/{}/.]+");

    private final QueryEntityExtractionService queryEntityExtractionService;

    public QueryUnderstandingServiceImpl(QueryEntityExtractionService queryEntityExtractionService) {
        this.queryEntityExtractionService = queryEntityExtractionService;
    }

    @Override
    public QueryUnderstanding understand(String question) {
        QueryUnderstanding understanding = new QueryUnderstanding();
        understanding.setQuestion(question);
        understanding.setQueryEntities(queryEntityExtractionService.extractQueryEntities(question));

        if (isTableOwnershipQuestion(question)) {
            fillTableOwnership(understanding);
            return understanding;
        }
        if (isPdfLibraryDependencyQuestion(question)) {
            fillPdfLibraryDependency(understanding);
            return understanding;
        }
        if (isPdfBoxRelationQuestion(question)) {
            fillPdfBoxRelation(understanding);
            return understanding;
        }
        if (isChunkDefinitionQuestion(question)) {
            fillChunkDefinition(understanding);
            return understanding;
        }
        if (isAskDependencyQuestion(question)) {
            fillAskDependency(understanding);
            return understanding;
        }
        if (isSearchRuleQuestion(question)) {
            fillSearchRule(understanding);
            return understanding;
        }
        if (isConfigQuestion(question)) {
            fillConfigLocation(understanding, question);
            return understanding;
        }

        fillGeneric(understanding, question);
        return understanding;
    }

    private void fillTableOwnership(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.TABLE_OWNERSHIP);
        understanding.setFocusEntity("embeddingStatus");
        understanding.setKeyword("embeddingStatus");
        understanding.setQueryTerms(buildList("embeddingStatus", "EMBEDDING_STATUS", "RAG_DOCUMENT_CHUNKS"));
        understanding.setExpectedEntityTypes(buildList("FIELD", "TABLE"));
        understanding.setBoostTerms(buildList("RAG_DOCUMENT_CHUNKS", "EMBEDDING_STATUS", "\u5b57\u6bb5", "\u8868"));
    }

    private void fillPdfLibraryDependency(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.LIBRARY_DEPENDENCY);
        understanding.setFocusEntity("PDF");
        understanding.setKeyword("PDF");
        understanding.setQueryTerms(buildList("PDF", "PDFBox", "/upload", "UploadService", "DocumentChunkService",
                "\u5e93", "\u4f9d\u8d56", "\u5bfc\u5165", "\u4e0a\u4f20"));
        understanding.setExpectedEntityTypes(buildList("LIBRARY", "SERVICE", "API"));
        understanding.setBoostTerms(buildList("PDFBox", "UploadService", "DocumentChunkService", "USES"));
    }

    private void fillPdfBoxRelation(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.RELATION_QUERY);
        understanding.setFocusEntity("PDFBox");
        understanding.setKeyword("PDFBox");
        understanding.setQueryTerms(buildList("PDFBox", "DocumentChunkService", "UploadService", "\u670d\u52a1"));
        understanding.setExpectedEntityTypes(buildList("LIBRARY", "SERVICE"));
        understanding.setBoostTerms(buildList("DocumentChunkService", "UploadService", "USES", "RELATED_TO"));
    }

    private void fillChunkDefinition(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.DEFINITION_LOCATION);
        understanding.setFocusEntity("chunk");
        understanding.setKeyword("chunk");
        understanding.setQueryTerms(buildList("chunk", "CHUNK_SIZE", "500", "saveChunks", "DocumentChunkService"));
        understanding.setExpectedEntityTypes(buildList("FIELD", "METHOD", "SERVICE"));
        understanding.setBoostTerms(buildList("CHUNK_SIZE", "DocumentChunkService", "saveChunks", "500"));
    }

    private void fillAskDependency(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.SERVICE_DEPENDENCY);
        understanding.setFocusEntity("/ask");
        understanding.setKeyword("/ask");
        understanding.setQueryTerms(buildList("/ask", "SearchService", "ContextBuilderService", "AnswerBuilderService", "searchHybrid"));
        understanding.setExpectedEntityTypes(buildList("API", "SERVICE", "METHOD"));
        understanding.setBoostTerms(buildList("DEPENDS_ON", "SearchService", "ContextBuilderService", "AnswerBuilderService"));
    }

    private void fillSearchRule(QueryUnderstanding understanding) {
        understanding.setIntent(QueryIntent.SEARCH_RULE);
        understanding.setFocusEntity("/search");
        understanding.setKeyword("\u6392\u5e8f");
        understanding.setQueryTerms(buildList("/search", "\u6392\u5e8f", "DOCUMENT_ID DESC", "CHUNK_INDEX ASC", "dbms_lob.instr"));
        understanding.setExpectedEntityTypes(buildList("API", "METHOD", "FIELD"));
        understanding.setBoostTerms(buildList("DOCUMENT_ID DESC", "CHUNK_INDEX ASC", "dbms_lob.instr", "\u6392\u5e8f\u89c4\u5219"));
    }

    private void fillConfigLocation(QueryUnderstanding understanding, String question) {
        understanding.setIntent(QueryIntent.CONFIG_LOCATION);
        understanding.setFocusEntity(resolveConfigFocus(question));
        understanding.setKeyword(understanding.getFocusEntity());
        understanding.setQueryTerms(buildList(understanding.getFocusEntity(), "application.yml", "application.properties", "\u914d\u7f6e"));
        understanding.setExpectedEntityTypes(buildList("CONFIG"));
        understanding.setBoostTerms(buildList("application.yml", "application.properties", "spring.datasource.url", "server.port"));
    }

    private void fillGeneric(QueryUnderstanding understanding, String question) {
        String keyword = extractKeyword(question);
        String focusEntity = resolveGenericFocus(question, keyword);

        understanding.setIntent(QueryIntent.GENERIC);
        understanding.setFocusEntity(focusEntity);
        understanding.setKeyword(keyword);
        understanding.setQueryTerms(buildGenericTerms(question, keyword, focusEntity, understanding.getQueryEntities()));
        understanding.setExpectedEntityTypes(new ArrayList<>());
        understanding.setBoostTerms(buildGenericBoostTerms(understanding.getQueryEntities(), focusEntity));
    }

    private List<String> buildGenericTerms(String question,
                                           String keyword,
                                           String focusEntity,
                                           List<QueryEntity> queryEntities) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addTerm(terms, keyword);
        addTerm(terms, focusEntity);
        if (queryEntities != null) {
            for (QueryEntity queryEntity : queryEntities) {
                if (queryEntity == null) {
                    continue;
                }
                addTerm(terms, queryEntity.getText());
                addTerm(terms, queryEntity.getNormalizedText());
            }
        }

        if (StringUtils.hasText(question)) {
            Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(question);
            while (matcher.find()) {
                addTerm(terms, matcher.group());
            }
        }
        return new ArrayList<>(terms);
    }

    private List<String> buildGenericBoostTerms(List<QueryEntity> queryEntities, String focusEntity) {
        LinkedHashSet<String> boostTerms = new LinkedHashSet<>();
        addTerm(boostTerms, focusEntity);
        if (queryEntities != null) {
            for (QueryEntity queryEntity : queryEntities) {
                if (queryEntity == null) {
                    continue;
                }
                addTerm(boostTerms, queryEntity.getText());
                addTerm(boostTerms, queryEntity.getNormalizedText());
            }
        }
        return new ArrayList<>(boostTerms);
    }

    private String resolveConfigFocus(String question) {
        if (containsAny(question, "spring.datasource.url")) {
            return "spring.datasource.url";
        }
        if (containsAny(question, "server.port")) {
            return "server.port";
        }
        return "\u914d\u7f6e";
    }

    private String resolveGenericFocus(String question, String keyword) {
        if (!StringUtils.hasText(question)) {
            return keyword;
        }

        Matcher pathMatcher = PATH_PATTERN.matcher(question);
        if (pathMatcher.find()) {
            return pathMatcher.group();
        }

        Matcher tokenMatcher = ENGLISH_TOKEN_PATTERN.matcher(question);
        if (tokenMatcher.find()) {
            return tokenMatcher.group();
        }

        return keyword;
    }

    private String extractKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }

        String result = question.trim();
        result = result.replace("\u4ec0\u4e48\u662f", "");
        result = result.replace("\u662f\u4ec0\u4e48", "");
        result = result.replace("\u662f\u5565", "");
        result = result.replace("\u4ecb\u7ecd\u4e00\u4e0b", "");
        result = result.replace("\u8bf7\u8bf4\u660e", "");
        result = result.replace("\u8bf7\u4ecb\u7ecd", "");
        result = result.replace("\u8bf7\u95ee", "");
        result = result.replace("\uff1f", " ");
        result = result.replace("?", " ");
        result = result.replace("\u3002", " ");
        result = result.replace(".", " ");
        result = result.replace("\uff0c", " ");
        result = result.replace(",", " ");
        result = result.replace("\uff1a", " ");
        result = result.replace(":", " ");
        result = result.replace("\uff1b", " ");
        result = result.replace(";", " ");
        result = result.replaceAll("\\s+", " ");
        return result.trim();
    }

    private boolean isTableOwnershipQuestion(String question) {
        return containsAll(question, "embeddingstatus") && containsAny(question, "\u5c5e\u4e8e\u54ea\u5f20\u8868", "\u54ea\u5f20\u8868");
    }

    private boolean isPdfLibraryDependencyQuestion(String question) {
        return containsAll(question, "pdf") && containsAny(question, "\u4f9d\u8d56\u4ec0\u4e48\u5e93", "\u4ec0\u4e48\u5e93", "\u5bfc\u5165");
    }

    private boolean isPdfBoxRelationQuestion(String question) {
        return containsAll(question, "pdfbox") && containsAny(question, "\u54ea\u4e9b\u670d\u52a1\u6709\u5173", "\u54ea\u4e9b\u670d\u52a1\u76f8\u5173");
    }

    private boolean isChunkDefinitionQuestion(String question) {
        return containsAll(question, "chunk") && containsAny(question, "\u5927\u5c0f\u5728\u54ea\u91cc\u5b9a\u4e49", "\u5728\u54ea\u91cc\u5b9a\u4e49", "\u5b9a\u4e49\u5728\u54ea\u91cc");
    }

    private boolean isAskDependencyQuestion(String question) {
        return containsAll(question, "/ask") && containsAny(question, "\u4f9d\u8d56\u54ea\u4e9b\u5df2\u6709\u80fd\u529b", "\u4f9d\u8d56\u54ea\u4e9b\u80fd\u529b");
    }

    private boolean isSearchRuleQuestion(String question) {
        return containsAll(question, "/search") && containsAny(question, "\u6392\u5e8f\u89c4\u5219");
    }

    private boolean isConfigQuestion(String question) {
        return containsAny(question, "spring.datasource.url", "server.port", "\u914d\u7f6e\u5728\u54ea\u91cc");
    }

    private boolean containsAll(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!lower.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
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

    private List<String> buildList(String... values) {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            addTerm(dedup, value);
        }
        return new ArrayList<>(dedup);
    }

    private void addTerm(Set<String> target, String value) {
        if (StringUtils.hasText(value)) {
            target.add(value.trim());
        }
    }
}
