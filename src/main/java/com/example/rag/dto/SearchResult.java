package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {

    private String keyword;
    private List<ChunkHit> matchedChunks = new ArrayList<>();
    private List<Long> matchedDocumentIds = new ArrayList<>();
    private SearchMeta searchMeta;

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

    public List<Long> getMatchedDocumentIds() {
        return matchedDocumentIds;
    }

    public void setMatchedDocumentIds(List<Long> matchedDocumentIds) {
        this.matchedDocumentIds = matchedDocumentIds;
    }

    public SearchMeta getSearchMeta() {
        return searchMeta;
    }

    public void setSearchMeta(SearchMeta searchMeta) {
        this.searchMeta = searchMeta;
    }
}
