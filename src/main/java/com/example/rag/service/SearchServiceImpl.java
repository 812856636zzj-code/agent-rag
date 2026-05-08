package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.EntityHit;
import com.example.rag.dto.SearchMeta;
import com.example.rag.dto.SearchResult;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentEntityLinkRepository;
import com.example.rag.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentEntityLinkRepository documentEntityLinkRepository;
    private final EntityExtractionService entityExtractionService;

    public SearchServiceImpl(DocumentChunkRepository documentChunkRepository,
                             DocumentEntityLinkRepository documentEntityLinkRepository,
                             EntityExtractionService entityExtractionService) {
        this.documentChunkRepository = documentChunkRepository;
        this.documentEntityLinkRepository = documentEntityLinkRepository;
        this.entityExtractionService = entityExtractionService;
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
        List<EntityHit> queryEntities = entityExtractionService.extractEntities(keywordOrQuestion);
        List<DocumentChunk> entityChunks = fetchEntityChunks(queryEntities);
        return buildSearchResult(cleanedKeyword, entityChunks, System.currentTimeMillis() - start);
    }

    @Override
    public SearchResult searchHybrid(String question) {
        long start = System.currentTimeMillis();
        String keyword = extractKeyword(question);
        List<DocumentChunk> keywordChunks = fetchKeywordChunks(keyword);
        List<EntityHit> queryEntities = entityExtractionService.extractEntities(question);
        Map<Long, Integer> entityHitCountMap = fetchEntityHitCountMap(queryEntities);
        List<DocumentChunk> entityChunks = fetchChunksByIds(new ArrayList<>(entityHitCountMap.keySet()));

        Set<Long> keywordChunkIds = keywordChunks.stream()
                .map(DocumentChunk::getId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, DocumentChunk> mergedChunkMap = new HashMap<>();
        for (DocumentChunk chunk : keywordChunks) {
            if (chunk != null && chunk.getId() != null) {
                mergedChunkMap.put(chunk.getId(), chunk);
            }
        }
        for (DocumentChunk chunk : entityChunks) {
            if (chunk != null && chunk.getId() != null) {
                mergedChunkMap.put(chunk.getId(), chunk);
            }
        }

        List<DocumentChunk> mergedChunks = new ArrayList<>(mergedChunkMap.values());
        mergedChunks.sort((left, right) -> compareHybrid(left, right, keywordChunkIds, entityHitCountMap));
        return buildSearchResult(keyword, mergedChunks, System.currentTimeMillis() - start);
    }

    @Override
    public SearchResult searchByQuestion(String question) {
        return searchHybrid(question);
    }

    private int compareHybrid(DocumentChunk left,
                              DocumentChunk right,
                              Set<Long> keywordChunkIds,
                              Map<Long, Integer> entityHitCountMap) {
        boolean leftKeyword = left.getId() != null && keywordChunkIds.contains(left.getId());
        boolean rightKeyword = right.getId() != null && keywordChunkIds.contains(right.getId());
        boolean leftEntity = left.getId() != null && entityHitCountMap.containsKey(left.getId());
        boolean rightEntity = right.getId() != null && entityHitCountMap.containsKey(right.getId());

        int leftBoth = leftKeyword && leftEntity ? 1 : 0;
        int rightBoth = rightKeyword && rightEntity ? 1 : 0;
        if (leftBoth != rightBoth) {
            return Integer.compare(rightBoth, leftBoth);
        }

        int leftEntityCount = left.getId() == null ? 0 : entityHitCountMap.getOrDefault(left.getId(), 0);
        int rightEntityCount = right.getId() == null ? 0 : entityHitCountMap.getOrDefault(right.getId(), 0);
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

    private List<DocumentChunk> fetchEntityChunks(List<EntityHit> queryEntities) {
        Map<Long, Integer> entityHitCountMap = fetchEntityHitCountMap(queryEntities);
        List<DocumentChunk> chunks = fetchChunksByIds(new ArrayList<>(entityHitCountMap.keySet()));
        chunks.sort(Comparator
                .comparingInt((DocumentChunk chunk) -> entityHitCountMap.getOrDefault(chunk.getId(), 0)).reversed()
                .thenComparing(chunk -> chunk.getChunkIndex() == null ? Integer.MAX_VALUE : chunk.getChunkIndex())
                .thenComparing((DocumentChunk chunk) -> chunk.getDocumentId() == null ? Long.MIN_VALUE : chunk.getDocumentId(), Comparator.reverseOrder()));
        return chunks;
    }

    private Map<Long, Integer> fetchEntityHitCountMap(List<EntityHit> queryEntities) {
        List<String> normalizedNames = queryEntities.stream()
                .map(EntityHit::getNormalizedName)
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Integer> entityHitCountMap = new HashMap<>();
        if (normalizedNames.isEmpty()) {
            return entityHitCountMap;
        }

        List<Object[]> rows = documentEntityLinkRepository.countEntityMatchesByNormalizedNames(normalizedNames);
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            Long chunkId = ((Number) row[0]).longValue();
            Integer entityCount = ((Number) row[1]).intValue();
            entityHitCountMap.put(chunkId, entityCount);
        }
        return entityHitCountMap;
    }

    private List<DocumentChunk> fetchChunksByIds(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<DocumentChunk> chunks = documentChunkRepository.findByIdIn(chunkIds);
        Map<Long, DocumentChunk> chunkMap = new HashMap<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk != null && chunk.getId() != null) {
                chunkMap.put(chunk.getId(), chunk);
            }
        }

        List<DocumentChunk> orderedChunks = new ArrayList<>();
        for (Long chunkId : chunkIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk != null) {
                orderedChunks.add(chunk);
            }
        }
        return orderedChunks;
    }

    private SearchResult buildSearchResult(String keyword, List<DocumentChunk> chunks, long costMs) {
        List<ChunkHit> matchedChunks = new ArrayList<>();
        LinkedHashSet<Long> matchedDocumentIdSet = new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            ChunkHit hit = new ChunkHit();
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
