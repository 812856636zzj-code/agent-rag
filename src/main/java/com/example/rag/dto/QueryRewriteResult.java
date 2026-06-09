package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class QueryRewriteResult {

    private String originalQuestion;
    private String rewrittenQuery;
    private List<String> expandedKeywords = new ArrayList<>();
    private List<String> searchQueries = new ArrayList<>();
    private String detectedIntent;
    private List<String> detectedEntities = new ArrayList<>();

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public void setOriginalQuestion(String originalQuestion) {
        this.originalQuestion = originalQuestion;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    public List<String> getExpandedKeywords() {
        return expandedKeywords;
    }

    public void setExpandedKeywords(List<String> expandedKeywords) {
        this.expandedKeywords = expandedKeywords;
    }

    public List<String> getSearchQueries() {
        return searchQueries;
    }

    public void setSearchQueries(List<String> searchQueries) {
        this.searchQueries = searchQueries;
    }

    public String getDetectedIntent() {
        return detectedIntent;
    }

    public void setDetectedIntent(String detectedIntent) {
        this.detectedIntent = detectedIntent;
    }

    public List<String> getDetectedEntities() {
        return detectedEntities;
    }

    public void setDetectedEntities(List<String> detectedEntities) {
        this.detectedEntities = detectedEntities;
    }
}
