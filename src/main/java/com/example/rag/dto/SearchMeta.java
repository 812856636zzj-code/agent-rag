package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class SearchMeta {

    private Integer totalHits;
    private List<Long> matchedDocumentIds = new ArrayList<>();

    public Integer getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(Integer totalHits) {
        this.totalHits = totalHits;
    }

    public List<Long> getMatchedDocumentIds() {
        return matchedDocumentIds;
    }

    public void setMatchedDocumentIds(List<Long> matchedDocumentIds) {
        this.matchedDocumentIds = matchedDocumentIds;
    }
}
