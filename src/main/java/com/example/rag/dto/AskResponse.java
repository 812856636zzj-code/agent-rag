package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AskResponse {

    private String question;
    private String keyword;
    private List<ChunkHit> chunks = new ArrayList<>();
    private String structuredContext;
    private String answer;
    private List<QueryEntity> queryEntities = new ArrayList<>();
    private Integer keywordHitCount;
    private Integer entityHitCount;
    private Integer mergedChunkCount;
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

    public List<ChunkHit> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkHit> chunks) {
        this.chunks = chunks;
    }

    public String getStructuredContext() {
        return structuredContext;
    }

    public void setStructuredContext(String structuredContext) {
        this.structuredContext = structuredContext;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<QueryEntity> getQueryEntities() {
        return queryEntities;
    }

    public void setQueryEntities(List<QueryEntity> queryEntities) {
        this.queryEntities = queryEntities;
    }

    public Integer getKeywordHitCount() {
        return keywordHitCount;
    }

    public void setKeywordHitCount(Integer keywordHitCount) {
        this.keywordHitCount = keywordHitCount;
    }

    public Integer getEntityHitCount() {
        return entityHitCount;
    }

    public void setEntityHitCount(Integer entityHitCount) {
        this.entityHitCount = entityHitCount;
    }

    public Integer getMergedChunkCount() {
        return mergedChunkCount;
    }

    public void setMergedChunkCount(Integer mergedChunkCount) {
        this.mergedChunkCount = mergedChunkCount;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }
}
