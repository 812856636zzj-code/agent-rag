package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class StructuredContext {

    private String question;
    private String keyword;
    private String intent;
    private String focusEntity;
    private List<String> matchedEntities = new ArrayList<>();
    private List<String> matchedRelations = new ArrayList<>();
    private List<ChunkHit> retrievedChunks = new ArrayList<>();
    private List<String> evidenceTexts = new ArrayList<>();
    private String summary;

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

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getFocusEntity() {
        return focusEntity;
    }

    public void setFocusEntity(String focusEntity) {
        this.focusEntity = focusEntity;
    }

    public List<String> getMatchedEntities() {
        return matchedEntities;
    }

    public void setMatchedEntities(List<String> matchedEntities) {
        this.matchedEntities = matchedEntities;
    }

    public List<String> getMatchedRelations() {
        return matchedRelations;
    }

    public void setMatchedRelations(List<String> matchedRelations) {
        this.matchedRelations = matchedRelations;
    }

    public List<ChunkHit> getRetrievedChunks() {
        return retrievedChunks;
    }

    public void setRetrievedChunks(List<ChunkHit> retrievedChunks) {
        this.retrievedChunks = retrievedChunks;
    }

    public List<String> getEvidenceTexts() {
        return evidenceTexts;
    }

    public void setEvidenceTexts(List<String> evidenceTexts) {
        this.evidenceTexts = evidenceTexts;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
