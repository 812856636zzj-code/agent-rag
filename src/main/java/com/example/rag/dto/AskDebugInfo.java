package com.example.rag.dto;

import com.example.rag.enums.ToolType;

import java.util.ArrayList;
import java.util.List;

public class AskDebugInfo {

    private String retrievalType;
    private Integer hitCount;
    private Integer relationHitCount;
    private Integer chunkHitCount;
    private Integer keywordHitCount;
    private Integer entityHitCount;
    private List<String> extractedEntityKeywords = new ArrayList<>();
    private Integer entityCandidateCount;
    private AgentRouteDecision routeDecision;
    private List<ToolType> executedTools = new ArrayList<>();

    public String getRetrievalType() {
        return retrievalType;
    }

    public void setRetrievalType(String retrievalType) {
        this.retrievalType = retrievalType;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public Integer getRelationHitCount() {
        return relationHitCount;
    }

    public void setRelationHitCount(Integer relationHitCount) {
        this.relationHitCount = relationHitCount;
    }

    public Integer getChunkHitCount() {
        return chunkHitCount;
    }

    public void setChunkHitCount(Integer chunkHitCount) {
        this.chunkHitCount = chunkHitCount;
    }

    public Integer getKeywordHitCount() {
        return keywordHitCount;
    }

    public void setKeywordHitCount(Integer keywordHitCount) {
        this.keywordHitCount = keywordHitCount;
    }

    public Integer getEntityHitCount() {
        return entityHitCount;
    }

    public void setEntityHitCount(Integer entityHitCount) {
        this.entityHitCount = entityHitCount;
    }

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

    public AgentRouteDecision getRouteDecision() {
        return routeDecision;
    }

    public void setRouteDecision(AgentRouteDecision routeDecision) {
        this.routeDecision = routeDecision;
    }

    public List<ToolType> getExecutedTools() {
        return executedTools;
    }

    public void setExecutedTools(List<ToolType> executedTools) {
        this.executedTools = executedTools;
    }
}
