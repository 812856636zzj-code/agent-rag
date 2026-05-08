package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.EntitySearchHit;
import com.example.rag.dto.HybridSearchResult;
import com.example.rag.dto.QueryEntity;
import com.example.rag.dto.SearchMeta;
import com.example.rag.dto.SearchResult;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentEntityLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentEntityLinkRepository documentEntityLinkRepository;
    private final QueryEntityExtractionService queryEntityExtractionService;

    public SearchServiceImpl(DocumentChunkRepository documentChunkRepository,
                             DocumentEntityLinkRepository documentEntityLinkRepository,
                             QueryEntityExtractionService queryEntityExtractionService) {
        this.documentChunkRepository = documentChunkRepository;
        this.documentEntityLinkRepository = documentEntityLinkRepository;
        this.queryEntityExtractionService = queryEntityExtractionService;
    }

    @Override
    public SearchResult searchByKeyword(String keyword) {
        long start = System.currentTimeMillis();
        String cleanedKeyword = normalizeKeyword(keyword);
        List<DocumentChunk> keywordChunks = fetchKeywordChunks(cleanedKeyword);
        return buildSearchResult(cleanedKeyword, keywordChunks, System.currentTimeMillis() - start);
    }

    @Override
    public SearchResult searchByEntity(String keywordOrQuestion) {
        long start = System.currentTimeMillis();
        String cleanedKeyword = normalizeKeyword(keywordOrQuestion);
        List<QueryEntity> queryEntities = queryEntityExtractionService.extractQueryEntities(keywordOrQuestion);
        log.info("entity search question = {}", keywordOrQuestion);
        log.info("entity search extracted query entities = {}", queryEntities.stream()
                .map(QueryEntity::getNormalizedText)
                .collect(Collectors.toList()));

        if (queryEntities.isEmpty()) {
            SearchResult emptyResult = buildSearchResult(cleanedKeyword, new ArrayList<>(), System.currentTimeMillis() - start);
            log.info("entity search hit rows = 0");
            log.info("entity search final chunk count = 0");
            return emptyResult;
        }

        List<Object[]> rows = fetchEntitySearchRows(queryEntities);
        log.info("entity search hit rows = {}", rows.size());

        List<EntitySearchHit> entityHits = toEntitySearchHits(rows);
        entityHits.sort(Comparator
                .comparingInt((EntitySearchHit hit) -> hit.getEntityHitCount() == null ? 0 : hit.getEntityHitCount()).reversed()
                .thenComparing((EntitySearchHit hit) -> hit.getDocumentId() == null ? Long.MIN_VALUE : hit.getDocumentId(), Comparator.reverseOrder())
                .thenComparing(hit -> hit.getChunkIndex() == null ? Integer.MAX_VALUE : hit.getChunkIndex()));

        List<ChunkHit> matchedChunks = new ArrayList<>();
        for (EntitySearchHit hit : entityHits) {
            ChunkHit chunkHit = new ChunkHit();
            chunkHit.setChunkId(hit.getChunkId());
            chunkHit.setDocumentId(hit.getDocumentId());
            chunkHit.setChunkIndex(hit.getChunkIndex());
            chunkHit.setContent(hit.getContent());
            chunkHit.setTokenCount(null);
            matchedChunks.add(chunkHit);
        }

        SearchMeta searchMeta = new SearchMeta();
        searchMeta.setKeyword(cleanedKeyword);
        searchMeta.setTotalHits(matchedChunks.size());
        searchMeta.setCostMs(System.currentTimeMillis() - start);

        SearchResult result = new SearchResult();
        result.setKeyword(cleanedKeyword);
        result.setMatchedChunks(matchedChunks);
        result.setMatchedDocumentIds(extractMatchedDocumentIds(matchedChunks));
        result.setSearchMeta(searchMeta);
        log.info("entity search final chunk count = {}", matchedChunks.size());
        return result;
    }

    @Override
    public HybridSearchResult searchHybrid(String question) {
        String keyword = extractKeyword(question);
        SearchResult keywordSearchResult = searchByKeyword(keyword);
        SearchResult entitySearchResult = searchByEntity(question);
        List<QueryEntity> queryEntities = queryEntityExtractionService.extractQueryEntities(question);
        List<Object[]> entityRows = fetchEntitySearchRows(queryEntities);
        List<EntitySearchHit> entityHits = toEntitySearchHits(entityRows);
        Map<Long, Integer> entityHitCountByChunkId = extractEntityHitCountMap(entityHits);

        List<ChunkHit> keywordHits = safeChunkHits(keywordSearchResult.getMatchedChunks());
        List<ChunkHit> entityChunks = safeChunkHits(entitySearchResult.getMatchedChunks());

        Set<String> keywordKeys = keywordHits.stream()
                .map(this::buildChunkKey)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> entityKeys = entityChunks.stream()
                .map(this::buildChunkKey)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, ChunkHit> mergedMap = new HashMap<>();
        for (ChunkHit hit : keywordHits) {
            mergedMap.put(buildChunkKey(hit), hit);
        }
        for (ChunkHit hit : entityChunks) {
            mergedMap.putIfAbsent(buildChunkKey(hit), hit);
        }

        List<ChunkHit> mergedChunks = new ArrayList<>(mergedMap.values());
        mergedChunks.sort((left, right) -> compareHybrid(left, right, keywordKeys, entityKeys, entityHitCountByChunkId));
        if (mergedChunks.size() > 10) {
            mergedChunks = new ArrayList<>(mergedChunks.subList(0, 10));
        }

        log.info("hybrid search question = {}", question);
        log.info("hybrid search keyword = {}", keyword);
        log.info("hybrid search keywordHits count = {}", keywordHits.size());
        log.info("hybrid search queryEntities = {}", queryEntities.stream()
                .map(QueryEntity::getNormalizedText)
                .collect(Collectors.toList()));
        log.info("hybrid search entityHits count = {}", entityHits.size());
        log.info("hybrid search mergedChunks count = {}", mergedChunks.size());

        HybridSearchResult result = new HybridSearchResult();
        result.setQuestion(question);
        result.setKeyword(keyword);
        result.setQueryEntities(queryEntities);
        result.setKeywordHits(keywordHits);
        result.setEntityHits(entityHits);
        result.setMergedChunks(mergedChunks);
        result.setEntityHitCountByChunkId(entityHitCountByChunkId);
        result.setRetrievalMode("HYBRID");
        return result;
    }

    @Override
    public SearchResult searchByQuestion(String question) {
        HybridSearchResult hybridSearchResult = searchHybrid(question);

        SearchMeta searchMeta = new SearchMeta();
        searchMeta.setKeyword(hybridSearchResult.getKeyword());
        searchMeta.setTotalHits(hybridSearchResult.getMergedChunks().size());

        SearchResult result = new SearchResult();
        result.setKeyword(hybridSearchResult.getKeyword());
        result.setMatchedChunks(hybridSearchResult.getMergedChunks());
        result.setMatchedDocumentIds(extractMatchedDocumentIds(hybridSearchResult.getMergedChunks()));
        result.setSearchMeta(searchMeta);
        return result;
    }

    private int compareHybrid(ChunkHit left,
                              ChunkHit right,
                              Set<String> keywordKeys,
                              Set<String> entityKeys,
                              Map<Long, Integer> entityHitCountByChunkId) {
        String leftKey = buildChunkKey(left);
        String rightKey = buildChunkKey(right);

        boolean leftKeyword = keywordKeys.contains(leftKey);
        boolean rightKeyword = keywordKeys.contains(rightKey);
        boolean leftEntity = entityKeys.contains(leftKey);
        boolean rightEntity = entityKeys.contains(rightKey);

        int leftBoth = leftKeyword && leftEntity ? 1 : 0;
        int rightBoth = rightKeyword && rightEntity ? 1 : 0;
        if (leftBoth != rightBoth) {
            return Integer.compare(rightBoth, leftBoth);
        }

        int leftEntityCount = left.getChunkId() == null ? 0 : entityHitCountByChunkId.getOrDefault(left.getChunkId(), 0);
        int rightEntityCount = right.getChunkId() == null ? 0 : entityHitCountByChunkId.getOrDefault(right.getChunkId(), 0);
        if (leftEntityCount != rightEntityCount) {
            return Integer.compare(rightEntityCount, leftEntityCount);
        }

        int leftChunkIndex = left.getChunkIndex() == null ? Integer.MAX_VALUE : left.getChunkIndex();
        int rightChunkIndex = right.getChunkIndex() == null ? Integer.MAX_VALUE : right.getChunkIndex();
        if (leftChunkIndex != rightChunkIndex) {
            return Integer.compare(leftChunkIndex, rightChunkIndex);
        }

        long leftDocumentId = left.getDocumentId() == null ? Long.MIN_VALUE : left.getDocumentId();
        long rightDocumentId = right.getDocumentId() == null ? Long.MIN_VALUE : right.getDocumentId();
        return Long.compare(rightDocumentId, leftDocumentId);
    }

    private List<DocumentChunk> fetchKeywordChunks(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        return documentChunkRepository.searchByKeyword(keyword);
    }

    private List<Object[]> fetchEntitySearchRows(List<QueryEntity> queryEntities) {
        List<String> normalizedNames = queryEntities.stream()
                .map(QueryEntity::getNormalizedText)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalizedNames.isEmpty()) {
            return new ArrayList<>();
        }
        return documentEntityLinkRepository.searchChunksByEntityNormalizedNames(normalizedNames);
    }

    private List<EntitySearchHit> toEntitySearchHits(List<Object[]> rows) {
        Map<Long, EntitySearchHit> hitMap = new HashMap<>();
        Map<Long, Set<String>> normalizedNameMap = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 7 || row[0] == null) {
                continue;
            }

            Long chunkId = ((Number) row[0]).longValue();
            EntitySearchHit hit = hitMap.get(chunkId);
            if (hit == null) {
                hit = new EntitySearchHit();
                hit.setChunkId(chunkId);
                hit.setDocumentId(row[1] == null ? null : ((Number) row[1]).longValue());
                hit.setChunkIndex(row[2] == null ? null : ((Number) row[2]).intValue());
                hit.setContent(clobToString(row[3]));
                hit.setEntityHitCount(0);
                hit.setScore(0.0d);
                hitMap.put(chunkId, hit);
                normalizedNameMap.put(chunkId, new HashSet<>());
            }

            String entityName = row[4] == null ? null : String.valueOf(row[4]);
            String entityType = row[5] == null ? null : String.valueOf(row[5]);
            String normalizedName = row[6] == null ? null : String.valueOf(row[6]);

            if (StringUtils.hasText(entityName) && !hit.getMatchedEntityNames().contains(entityName)) {
                hit.getMatchedEntityNames().add(entityName);
            }
            if (StringUtils.hasText(entityType) && !hit.getMatchedEntityTypes().contains(entityType)) {
                hit.getMatchedEntityTypes().add(entityType);
            }
            Set<String> normalizedNames = normalizedNameMap.get(chunkId);
            if (StringUtils.hasText(normalizedName) && normalizedNames.add(normalizedName)) {
                hit.setEntityHitCount(hit.getEntityHitCount() + 1);
                hit.setScore(hit.getEntityHitCount().doubleValue());
            }
        }
        return new ArrayList<>(hitMap.values());
    }

    private Map<Long, Integer> extractEntityHitCountMap(List<EntitySearchHit> entitySearchHits) {
        Map<Long, Integer> entityHitCountByChunkId = new HashMap<>();
        for (EntitySearchHit hit : entitySearchHits) {
            if (hit.getChunkId() != null) {
                entityHitCountByChunkId.put(hit.getChunkId(), hit.getEntityHitCount() == null ? 0 : hit.getEntityHitCount());
            }
        }
        return entityHitCountByChunkId;
    }

    private SearchResult buildSearchResult(String keyword, List<DocumentChunk> chunks, long costMs) {
        List<ChunkHit> matchedChunks = new ArrayList<>();
        LinkedHashSet<Long> matchedDocumentIdSet = new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            ChunkHit hit = new ChunkHit();
            hit.setChunkId(chunk.getId());
            hit.setDocumentId(chunk.getDocumentId());
            hit.setChunkIndex(chunk.getChunkIndex());
            hit.setContent(chunk.getContent());
            hit.setTokenCount(chunk.getTokenCount());
            matchedChunks.add(hit);
            if (chunk.getDocumentId() != null) {
                matchedDocumentIdSet.add(chunk.getDocumentId());
            }
        }

        List<Long> matchedDocumentIds = new ArrayList<>(matchedDocumentIdSet);
        SearchMeta searchMeta = new SearchMeta();
        searchMeta.setKeyword(keyword);
        searchMeta.setTotalHits(matchedChunks.size());
        searchMeta.setCostMs(costMs);

        SearchResult result = new SearchResult();
        result.setKeyword(keyword);
        result.setMatchedChunks(matchedChunks);
        result.setMatchedDocumentIds(matchedDocumentIds);
        result.setSearchMeta(searchMeta);
        return result;
    }

    private List<Long> extractMatchedDocumentIds(List<ChunkHit> matchedChunks) {
        LinkedHashSet<Long> matchedDocumentIdSet = new LinkedHashSet<>();
        for (ChunkHit chunk : matchedChunks) {
            if (chunk.getDocumentId() != null) {
                matchedDocumentIdSet.add(chunk.getDocumentId());
            }
        }
        return new ArrayList<>(matchedDocumentIdSet);
    }

    private List<ChunkHit> safeChunkHits(List<ChunkHit> hits) {
        return hits == null ? new ArrayList<>() : new ArrayList<>(hits);
    }

    private String clobToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Clob) {
            Clob clob = (Clob) value;
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException ex) {
                throw new IllegalStateException("read clob content failed", ex);
            }
        }
        return String.valueOf(value);
    }

    private String buildChunkKey(ChunkHit hit) {
        if (hit.getChunkId() != null) {
            return "id:" + hit.getChunkId();
        }
        return "doc:" + hit.getDocumentId() + ":idx:" + hit.getChunkIndex();
    }

    private String extractKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }

        String originalQuestion = question.trim();
        String cleaned = removeQuestionWords(originalQuestion);
        cleaned = removePunctuation(cleaned);
        cleaned = cleaned.trim();

        if (StringUtils.hasText(cleaned)) {
            return cleaned;
        }

        String fallback = removePunctuation(originalQuestion).trim();
        return StringUtils.hasText(fallback) ? fallback : originalQuestion;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return removePunctuation(keyword).trim();
    }

    private String removeQuestionWords(String text) {
        String result = text;
        result = result.replace("\u4ec0\u4e48\u662f", "");
        result = result.replace("\u662f\u4ec0\u4e48", "");
        result = result.replace("\u662f\u5565", "");
        result = result.replace("\u4ecb\u7ecd\u4e00\u4e0b", "");
        result = result.replace("\u8bf7\u8bf4\u660e", "");
        result = result.replace("\u8bf7\u4ecb\u7ecd", "");
        result = result.replace("\u8bf7\u95ee", "");
        result = result.replaceAll("(?i)\\bhow\\b", "");
        result = result.replaceAll("(?i)\\bwhat\\b", "");
        result = result.replaceAll("(?i)\\bwhy\\b", "");
        result = result.replaceAll("(?i)\\bwho\\b", "");
        result = result.replaceAll("(?i)\\bwhen\\b", "");
        result = result.replaceAll("(?i)\\bwhere\\b", "");
        return result;
    }

    private String removePunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String result = text;
        result = result.replace("?", " ");
        result = result.replace("\uff1f", " ");
        result = result.replace(".", " ");
        result = result.replace("\u3002", " ");
        result = result.replace(",", " ");
        result = result.replace("\uff0c", " ");
        result = result.replace("!", " ");
        result = result.replace("\uff01", " ");
        result = result.replace(":", " ");
        result = result.replace("\uff1a", " ");
        result = result.replace(";", " ");
        result = result.replace("\uff1b", " ");
        result = result.replace("\u3001", " ");
        result = result.replace("\u201c", " ");
        result = result.replace("\u201d", " ");
        result = result.replace("\"", " ");
        result = result.replace("'", " ");
        result = result.replace("\uff08", " ");
        result = result.replace("\uff09", " ");
        result = result.replace("(", " ");
        result = result.replace(")", " ");
        result = result.replace("[", " ");
        result = result.replace("]", " ");
        result = result.replace("\u3010", " ");
        result = result.replace("\u3011", " ");
        result = result.replaceAll("\\s+", " ");
        return result;
    }
}
