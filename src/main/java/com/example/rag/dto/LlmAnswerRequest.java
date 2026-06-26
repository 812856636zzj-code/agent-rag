package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class LlmAnswerRequest {

    private String question;
    private List<ChunkHit> topChunks = new ArrayList<>();
    private List<RelationHit> relationEvidence = new ArrayList<>();
    private List<SourceItem> sources = new ArrayList<>();
    private String structuredContext;
    private String ruleBasedAnswer;
    private String traceContext;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<ChunkHit> getTopChunks() {
        return topChunks;
    }

    public void setTopChunks(List<ChunkHit> topChunks) {
        this.topChunks = topChunks;
    }

    public List<RelationHit> getRelationEvidence() {
        return relationEvidence;
    }

    public void setRelationEvidence(List<RelationHit> relationEvidence) {
        this.relationEvidence = relationEvidence;
    }

    public List<SourceItem> getSources() {
        return sources;
    }

    public void setSources(List<SourceItem> sources) {
        this.sources = sources;
    }

    public String getStructuredContext() {
        return structuredContext;
    }

    public void setStructuredContext(String structuredContext) {
        this.structuredContext = structuredContext;
    }

    public String getRuleBasedAnswer() {
        return ruleBasedAnswer;
    }

    public void setRuleBasedAnswer(String ruleBasedAnswer) {
        this.ruleBasedAnswer = ruleBasedAnswer;
    }

    public String getTraceContext() {
        return traceContext;
    }

    public void setTraceContext(String traceContext) {
        this.traceContext = traceContext;
    }
}
