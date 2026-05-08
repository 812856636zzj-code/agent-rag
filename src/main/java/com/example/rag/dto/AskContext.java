package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AskContext {

    private String question;
    private String keyword;
    private List<ChunkHit> matchedChunks = new ArrayList<>();
    private List<ChunkHit> topChunks = new ArrayList<>();
    private String structuredContext;
    private List<Long> sourceDocIds = new ArrayList<>();
    private List<String> answerHints = new ArrayList<>();

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

    public List<ChunkHit> getMatchedChunks() {
        return matchedChunks;
    }

    public void setMatchedChunks(List<ChunkHit> matchedChunks) {
        this.matchedChunks = matchedChunks;
    }

    public List<ChunkHit> getTopChunks() {
        return topChunks;
    }

    public void setTopChunks(List<ChunkHit> topChunks) {
        this.topChunks = topChunks;
    }

    public String getStructuredContext() {
        return structuredContext;
    }

    public void setStructuredContext(String structuredContext) {
        this.structuredContext = structuredContext;
    }

    public List<Long> getSourceDocIds() {
        return sourceDocIds;
    }

    public void setSourceDocIds(List<Long> sourceDocIds) {
        this.sourceDocIds = sourceDocIds;
    }

    public List<String> getAnswerHints() {
        return answerHints;
    }

    public void setAnswerHints(List<String> answerHints) {
        this.answerHints = answerHints;
    }
}
