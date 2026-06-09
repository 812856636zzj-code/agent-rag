package com.example.rag.dto;

public class ChunkHit {

    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private RetrievalScoreDetail scoreDetail;
    private String matchedQuery;

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

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public RetrievalScoreDetail getScoreDetail() {
        return scoreDetail;
    }

    public void setScoreDetail(RetrievalScoreDetail scoreDetail) {
        this.scoreDetail = scoreDetail;
    }

    public String getMatchedQuery() {
        return matchedQuery;
    }

    public void setMatchedQuery(String matchedQuery) {
        this.matchedQuery = matchedQuery;
    }
}
