package com.example.rag.dto;

public class ChunkResponse {

    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String highlightContent;

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

    public String getHighlightContent() {
        return highlightContent;
    }

    public void setHighlightContent(String highlightContent) {
        this.highlightContent = highlightContent;
    }
}
