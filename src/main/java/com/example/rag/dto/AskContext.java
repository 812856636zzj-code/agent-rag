package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AskContext {

    private String question;
    private String keyword;
    private List<ChunkResponse> topChunks = new ArrayList<>();
    private List<Long> sourceDocIds = new ArrayList<>();
    private String structuredContext;

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

    public List<ChunkResponse> getTopChunks() {
        return topChunks;
    }

    public void setTopChunks(List<ChunkResponse> topChunks) {
        this.topChunks = topChunks;
    }

    public List<Long> getSourceDocIds() {
        return sourceDocIds;
    }

    public void setSourceDocIds(List<Long> sourceDocIds) {
        this.sourceDocIds = sourceDocIds;
    }

    public String getStructuredContext() {
        return structuredContext;
    }

    public void setStructuredContext(String structuredContext) {
        this.structuredContext = structuredContext;
    }
}
