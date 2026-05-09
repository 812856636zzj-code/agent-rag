package com.example.rag.dto;

import com.example.rag.enums.QueryIntent;

import java.util.ArrayList;
import java.util.List;

public class QueryUnderstanding {

    private String question;
    private String keyword;
    private QueryIntent intent;
    private String focusEntity;
    private List<String> queryTerms = new ArrayList<>();
    private List<String> expectedEntityTypes = new ArrayList<>();
    private List<String> boostTerms = new ArrayList<>();
    private List<QueryEntity> queryEntities = new ArrayList<>();

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

    public QueryIntent getIntent() {
        return intent;
    }

    public void setIntent(QueryIntent intent) {
        this.intent = intent;
    }

    public String getFocusEntity() {
        return focusEntity;
    }

    public void setFocusEntity(String focusEntity) {
        this.focusEntity = focusEntity;
    }

    public List<String> getQueryTerms() {
        return queryTerms;
    }

    public void setQueryTerms(List<String> queryTerms) {
        this.queryTerms = queryTerms;
    }

    public List<String> getExpectedEntityTypes() {
        return expectedEntityTypes;
    }

    public void setExpectedEntityTypes(List<String> expectedEntityTypes) {
        this.expectedEntityTypes = expectedEntityTypes;
    }

    public List<String> getBoostTerms() {
        return boostTerms;
    }

    public void setBoostTerms(List<String> boostTerms) {
        this.boostTerms = boostTerms;
    }

    public List<QueryEntity> getQueryEntities() {
        return queryEntities;
    }

    public void setQueryEntities(List<QueryEntity> queryEntities) {
        this.queryEntities = queryEntities;
    }
}
