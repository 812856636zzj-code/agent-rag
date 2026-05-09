package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class RelationSearchResult {

    private List<String> extractedEntityKeywords = new ArrayList<>();
    private Integer entityCandidateCount = 0;
    private List<RelationHit> relationHits = new ArrayList<>();

    public List<String> getExtractedEntityKeywords() {
        return extractedEntityKeywords;
    }

    public void setExtractedEntityKeywords(List<String> extractedEntityKeywords) {
        this.extractedEntityKeywords = extractedEntityKeywords;
    }

    public Integer getEntityCandidateCount() {
        return entityCandidateCount;
    }

    public void setEntityCandidateCount(Integer entityCandidateCount) {
        this.entityCandidateCount = entityCandidateCount;
    }

    public List<RelationHit> getRelationHits() {
        return relationHits;
    }

    public void setRelationHits(List<RelationHit> relationHits) {
        this.relationHits = relationHits;
    }
}
