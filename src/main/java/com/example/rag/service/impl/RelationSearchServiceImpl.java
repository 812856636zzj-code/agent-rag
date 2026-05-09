package com.example.rag.service.impl;

import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationSearchResult;
import com.example.rag.entity.RagEntity;
import com.example.rag.repository.RagEntityRelationRepository;
import com.example.rag.repository.RagEntityRepository;
import com.example.rag.service.QueryEntityExtractionService;
import com.example.rag.service.RelationSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RelationSearchServiceImpl implements RelationSearchService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(/[A-Za-z0-9_\\-/.]+)|([A-Za-z][A-Za-z0-9_]{2,})|([A-Z][A-Z0-9_]{2,})");
    private static final Pattern DATABASE_COLUMN_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> SQL_KEYWORD_BLACKLIST = new HashSet<>(Arrays.asList(
            "ORDER", "BY", "ASC", "DESC", "SELECT", "FROM", "WHERE", "GROUP", "HAVING",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL",
            "IS", "IN", "AS"
    ));
    private static final Set<String> DOCUMENT_TYPE_BLACKLIST = new HashSet<>(Arrays.asList(
            "PDF", "MD", "JSON", "TXT", "DOC", "DOCX"
    ));

    private final QueryEntityExtractionService queryEntityExtractionService;
    private final RagEntityRepository ragEntityRepository;
    private final RagEntityRelationRepository ragEntityRelationRepository;

    public RelationSearchServiceImpl(QueryEntityExtractionService queryEntityExtractionService,
                                     RagEntityRepository ragEntityRepository,
                                     RagEntityRelationRepository ragEntityRelationRepository) {
        this.queryEntityExtractionService = queryEntityExtractionService;
        this.ragEntityRepository = ragEntityRepository;
        this.ragEntityRelationRepository = ragEntityRelationRepository;
    }

    @Override
    public List<RelationHit> searchRelations(String question) {
        return searchRelationsWithDebug(question).getRelationHits();
    }

    @Override
    public RelationSearchResult searchRelationsWithDebug(String question) {
        RelationSearchResult result = new RelationSearchResult();
        List<String> entityKeywords = extractEntityKeywords(question);
        result.setExtractedEntityKeywords(entityKeywords);
        if (entityKeywords.isEmpty()) {
            return result;
        }

        Map<Long, RagEntity> entityMap = new LinkedHashMap<>();
        for (String keyword : entityKeywords) {
            List<RagEntity> candidates = ragEntityRepository.searchEntityCandidates(keyword, "%" + keyword + "%");
            for (RagEntity candidate : candidates) {
                if (candidate != null && candidate.getId() != null) {
                    entityMap.putIfAbsent(candidate.getId(), candidate);
                }
            }
        }
        result.setEntityCandidateCount(entityMap.size());

        if (entityMap.isEmpty()) {
            return result;
        }

        List<Object[]> rows = ragEntityRelationRepository.findRelationsByEntityIds(new ArrayList<>(entityMap.keySet()));
        List<RelationHit> hits = new ArrayList<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 13) {
                continue;
            }
            RelationHit hit = new RelationHit();
            hit.setSourceEntityId(numberToLong(row[1]));
            hit.setSourceEntityName(toText(row[2]));
            hit.setSourceEntityType(toText(row[3]));
            hit.setTargetEntityId(numberToLong(row[4]));
            hit.setTargetEntityName(toText(row[5]));
            hit.setTargetEntityType(toText(row[6]));
            hit.setRelationType(toText(row[7]));
            hit.setEvidenceText(toText(row[8]));
            hit.setDocumentId(numberToLong(row[9]));
            hit.setChunkId(numberToLong(row[10]));
            hit.setConfidence(numberToDouble(row[11]));
            hit.setCreateTime(row[12] instanceof Date ? (Date) row[12] : null);
            if (shouldSkipDirtyTableHasField(hit)) {
                continue;
            }
            hits.add(hit);
        }

        hits.sort((left, right) -> compareRelationPriority(left, right, entityKeywords));
        result.setRelationHits(hits);
        return result;
    }

    private List<String> extractEntityKeywords(String question) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (!StringUtils.hasText(question)) {
            return new ArrayList<>();
        }

        queryEntityExtractionService.extractQueryEntities(question).forEach(entity -> {
            if (entity != null) {
                addKeyword(keywords, entity.getText());
                addKeyword(keywords, entity.getNormalizedText());
            }
        });

        Matcher matcher = TOKEN_PATTERN.matcher(question);
        while (matcher.find()) {
            addKeyword(keywords, matcher.group());
        }

        if (containsAny(question, "哪些表", "哪些字段", "哪些类", "哪些方法", "哪些文档", "哪些chunk", "字段有哪些", "表有哪些字段")) {
            addKeyword(keywords, normalizeQuestionKeyword(question));
        }

        return new ArrayList<>(keywords);
    }

    private void addKeyword(Set<String> keywords, String value) {
        if (StringUtils.hasText(value)) {
            keywords.add(value.trim());
        }
    }

    private boolean containsAny(String text, String... values) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (lower.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeQuestionKeyword(String question) {
        return question.replace("关联", " ")
                .replace("哪些", " ")
                .replace("有什么", " ")
                .replace("在哪些", " ")
                .replace("出现", " ")
                .replace("字段", " ")
                .replace("表", " ")
                .replace("类", " ")
                .replace("方法", " ")
                .replace("文档", " ")
                .replace("chunk", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int compareRelationPriority(RelationHit left, RelationHit right, List<String> entityKeywords) {
        int leftScore = relationPriorityScore(left, entityKeywords);
        int rightScore = relationPriorityScore(right, entityKeywords);
        if (leftScore != rightScore) {
            return Integer.compare(rightScore, leftScore);
        }
        double leftConfidence = left.getConfidence() == null ? -1d : left.getConfidence();
        double rightConfidence = right.getConfidence() == null ? -1d : right.getConfidence();
        if (Double.compare(leftConfidence, rightConfidence) != 0) {
            return Double.compare(rightConfidence, leftConfidence);
        }
        long leftDoc = left.getDocumentId() == null ? Long.MIN_VALUE : left.getDocumentId();
        long rightDoc = right.getDocumentId() == null ? Long.MIN_VALUE : right.getDocumentId();
        if (leftDoc != rightDoc) {
            return Long.compare(rightDoc, leftDoc);
        }
        long leftChunk = left.getChunkId() == null ? Long.MAX_VALUE : left.getChunkId();
        long rightChunk = right.getChunkId() == null ? Long.MAX_VALUE : right.getChunkId();
        return Long.compare(leftChunk, rightChunk);
    }

    private int relationPriorityScore(RelationHit hit, List<String> entityKeywords) {
        int score = 0;
        if ("TABLE_HAS_FIELD".equalsIgnoreCase(hit.getRelationType())) {
            score += 20;
        }
        String source = toText(hit.getSourceEntityName());
        for (String keyword : entityKeywords) {
            if (StringUtils.hasText(keyword) && source.equalsIgnoreCase(keyword.trim())) {
                score += 10;
            }
        }
        return score;
    }

    private Long numberToLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private Double numberToDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean shouldSkipDirtyTableHasField(RelationHit hit) {
        if (hit == null || !"TABLE_HAS_FIELD".equalsIgnoreCase(hit.getRelationType())) {
            return false;
        }
        if (!"TABLE".equalsIgnoreCase(hit.getSourceEntityType())) {
            return true;
        }
        if (!"FIELD".equalsIgnoreCase(normalizeEntityType(hit.getTargetEntityType()))) {
            return true;
        }
        return !isDatabaseColumnName(hit.getTargetEntityName());
    }

    private String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        if ("COLUMN".equalsIgnoreCase(entityType)) {
            return "FIELD";
        }
        return entityType.toUpperCase(Locale.ROOT);
    }

    private boolean isDatabaseColumnName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return DATABASE_COLUMN_PATTERN.matcher(normalized).matches()
                && !SQL_KEYWORD_BLACKLIST.contains(normalized)
                && !DOCUMENT_TYPE_BLACKLIST.contains(normalized);
    }
}
