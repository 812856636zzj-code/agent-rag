package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class QueryUnderstandingResult {

    private String question;
    private String keyword;
    private String intentType;
    private String focusTerm;
    private List<QueryEntity> queryEntities = new ArrayList<>();
    private List<String> focusTerms = new ArrayList<>();

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

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public String getFocusTerm() {
        return focusTerm;
    }

    public void setFocusTerm(String focusTerm) {
        this.focusTerm = focusTerm;
    }

    public List<QueryEntity> getQueryEntities() {
        return queryEntities;
    }

    public void setQueryEntities(List<QueryEntity> queryEntities) {
        this.queryEntities = queryEntities;
    }

    public List<String> getFocusTerms() {
        return focusTerms;
    }

    public void setFocusTerms(List<String> focusTerms) {
        this.focusTerms = focusTerms;
    }
}
