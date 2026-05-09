package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class SourceItem {

    private Long documentId;
    private Long chunkId;
    private String documentName;
    private String content;
    private Double score;
    private String sourceType;
    private List<String> evidenceSentences = new ArrayList<>();

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<String> getEvidenceSentences() {
        return evidenceSentences;
    }

    public void setEvidenceSentences(List<String> evidenceSentences) {
        this.evidenceSentences = evidenceSentences;
    }
}
