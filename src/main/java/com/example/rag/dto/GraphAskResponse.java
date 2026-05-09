package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class GraphAskResponse extends AskResponse {

    private String intent;
    private String focusEntity;
    private List<String> matchedEntities = new ArrayList<>();
    private List<String> matchedRelations = new ArrayList<>();
    private Integer relationHitCount;

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

    public Integer getRelationHitCount() {
        return relationHitCount;
    }

    public void setRelationHitCount(Integer relationHitCount) {
        this.relationHitCount = relationHitCount;
    }
}
