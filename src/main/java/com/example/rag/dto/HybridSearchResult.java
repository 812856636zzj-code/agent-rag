package com.example.rag.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridSearchResult {

    private String question;
    private String keyword;
    private List<QueryEntity> queryEntities = new ArrayList<>();
    private List<ChunkHit> keywordHits = new ArrayList<>();
    private List<EntitySearchHit> entityHits = new ArrayList<>();
    private List<ChunkHit> mergedChunks = new ArrayList<>();
    private Map<Long, Integer> entityHitCountByChunkId = new LinkedHashMap<>();
    private String retrievalMode;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<QueryEntity> getQueryEntities() {
        return queryEntities;
    }

    public void setQueryEntities(List<QueryEntity> queryEntities) {
        this.queryEntities = queryEntities;
    }

    public List<ChunkHit> getKeywordHits() {
        return keywordHits;
    }

    public void setKeywordHits(List<ChunkHit> keywordHits) {
        this.keywordHits = keywordHits;
    }

    public List<EntitySearchHit> getEntityHits() {
        return entityHits;
    }

    public void setEntityHits(List<EntitySearchHit> entityHits) {
        this.entityHits = entityHits;
    }

    public List<ChunkHit> getMergedChunks() {
        return mergedChunks;
    }

    public void setMergedChunks(List<ChunkHit> mergedChunks) {
        this.mergedChunks = mergedChunks;
    }

    public Map<Long, Integer> getEntityHitCountByChunkId() {
        return entityHitCountByChunkId;
    }

    public void setEntityHitCountByChunkId(Map<Long, Integer> entityHitCountByChunkId) {
        this.entityHitCountByChunkId = entityHitCountByChunkId;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }
}
