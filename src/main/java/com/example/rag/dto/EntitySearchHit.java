package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class EntitySearchHit {

    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer entityHitCount;
    private List<String> matchedEntityNames = new ArrayList<>();
    private List<String> matchedEntityTypes = new ArrayList<>();
    private Double score;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getEntityHitCount() {
        return entityHitCount;
    }

    public void setEntityHitCount(Integer entityHitCount) {
        this.entityHitCount = entityHitCount;
    }

    public List<String> getMatchedEntityNames() {
        return matchedEntityNames;
    }

    public void setMatchedEntityNames(List<String> matchedEntityNames) {
        this.matchedEntityNames = matchedEntityNames;
    }

    public List<String> getMatchedEntityTypes() {
        return matchedEntityTypes;
    }

    public void setMatchedEntityTypes(List<String> matchedEntityTypes) {
        this.matchedEntityTypes = matchedEntityTypes;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
