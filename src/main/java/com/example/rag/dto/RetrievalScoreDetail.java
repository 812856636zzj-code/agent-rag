package com.example.rag.dto;

public class RetrievalScoreDetail {

    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private double keywordScore;
    private double entityScore;
    private double relationScore;
    private double sourceScore;
    private double keywordCoverage;
    private double entityCoverage;
    private double intentMatch;
    private String sourceType;
    private double noisePenalty;
    private double finalScore;
    private String notes;

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

    public double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public double getEntityScore() {
        return entityScore;
    }

    public void setEntityScore(double entityScore) {
        this.entityScore = entityScore;
    }

    public double getRelationScore() {
        return relationScore;
    }

    public void setRelationScore(double relationScore) {
        this.relationScore = relationScore;
    }

    public double getSourceScore() {
        return sourceScore;
    }

    public void setSourceScore(double sourceScore) {
        this.sourceScore = sourceScore;
    }

    public double getKeywordCoverage() {
        return keywordCoverage;
    }

    public void setKeywordCoverage(double keywordCoverage) {
        this.keywordCoverage = keywordCoverage;
    }

    public double getEntityCoverage() {
        return entityCoverage;
    }

    public void setEntityCoverage(double entityCoverage) {
        this.entityCoverage = entityCoverage;
    }

    public double getIntentMatch() {
        return intentMatch;
    }

    public void setIntentMatch(double intentMatch) {
        this.intentMatch = intentMatch;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public double getNoisePenalty() {
        return noisePenalty;
    }

    public void setNoisePenalty(double noisePenalty) {
        this.noisePenalty = noisePenalty;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
