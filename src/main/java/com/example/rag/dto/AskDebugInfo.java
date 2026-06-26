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
    private AgentExecutionTrace executionTrace;
    private String rewrittenQuery;
    private List<String> expandedKeywords = new ArrayList<>();
    private List<String> searchQueries = new ArrayList<>();
    private List<String> matchedQueries = new ArrayList<>();
    private boolean recallFallbackUsed;
    private java.util.Map<String, Integer> candidateCountByQuery = new java.util.LinkedHashMap<>();
    private int candidateCountAfterMerge;
    private int duplicateCandidateRemovedCount;
    private int uniqueCandidateCount;
    private double candidateScoreSpread;
    private double top1Score;
    private double top2Score;
    private double topScoreGap;
    private boolean rerankChanged;
    private String rerankChangeReason;
    private String detectedIntent;
    private List<String> detectedEntities = new ArrayList<>();
    private List<RetrievalScoreDetail> topScoreDetails = new ArrayList<>();
    private List<Long> rerankBeforeChunkIds = new ArrayList<>();
    private List<Long> rerankAfterChunkIds = new ArrayList<>();
    private int memoriesQueried;
    private List<AgentMemoryItem> memoriesUsed = new ArrayList<>();
    private String memoryMatchReason;
    private String answerProvider;
    private String answerModelName;
    private Long answerLatencyMs;
    private boolean answerFallbackUsed;
    private String answerErrorMessage;

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

    public AgentExecutionTrace getExecutionTrace() {
        return executionTrace;
    }

    public void setExecutionTrace(AgentExecutionTrace executionTrace) {
        this.executionTrace = executionTrace;
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

    public List<String> getMatchedQueries() {
        return matchedQueries;
    }

    public void setMatchedQueries(List<String> matchedQueries) {
        this.matchedQueries = matchedQueries;
    }

    public boolean isRecallFallbackUsed() {
        return recallFallbackUsed;
    }

    public void setRecallFallbackUsed(boolean recallFallbackUsed) {
        this.recallFallbackUsed = recallFallbackUsed;
    }

    public java.util.Map<String, Integer> getCandidateCountByQuery() {
        return candidateCountByQuery;
    }

    public void setCandidateCountByQuery(java.util.Map<String, Integer> candidateCountByQuery) {
        this.candidateCountByQuery = candidateCountByQuery;
    }

    public int getCandidateCountAfterMerge() {
        return candidateCountAfterMerge;
    }

    public void setCandidateCountAfterMerge(int candidateCountAfterMerge) {
        this.candidateCountAfterMerge = candidateCountAfterMerge;
    }

    public int getDuplicateCandidateRemovedCount() {
        return duplicateCandidateRemovedCount;
    }

    public void setDuplicateCandidateRemovedCount(int duplicateCandidateRemovedCount) {
        this.duplicateCandidateRemovedCount = duplicateCandidateRemovedCount;
    }

    public int getUniqueCandidateCount() {
        return uniqueCandidateCount;
    }

    public void setUniqueCandidateCount(int uniqueCandidateCount) {
        this.uniqueCandidateCount = uniqueCandidateCount;
    }

    public double getCandidateScoreSpread() {
        return candidateScoreSpread;
    }

    public void setCandidateScoreSpread(double candidateScoreSpread) {
        this.candidateScoreSpread = candidateScoreSpread;
    }

    public double getTop1Score() {
        return top1Score;
    }

    public void setTop1Score(double top1Score) {
        this.top1Score = top1Score;
    }

    public double getTop2Score() {
        return top2Score;
    }

    public void setTop2Score(double top2Score) {
        this.top2Score = top2Score;
    }

    public double getTopScoreGap() {
        return topScoreGap;
    }

    public void setTopScoreGap(double topScoreGap) {
        this.topScoreGap = topScoreGap;
    }

    public boolean isRerankChanged() {
        return rerankChanged;
    }

    public void setRerankChanged(boolean rerankChanged) {
        this.rerankChanged = rerankChanged;
    }

    public String getRerankChangeReason() {
        return rerankChangeReason;
    }

    public void setRerankChangeReason(String rerankChangeReason) {
        this.rerankChangeReason = rerankChangeReason;
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

    public List<RetrievalScoreDetail> getTopScoreDetails() {
        return topScoreDetails;
    }

    public void setTopScoreDetails(List<RetrievalScoreDetail> topScoreDetails) {
        this.topScoreDetails = topScoreDetails;
    }

    public List<Long> getRerankBeforeChunkIds() {
        return rerankBeforeChunkIds;
    }

    public void setRerankBeforeChunkIds(List<Long> rerankBeforeChunkIds) {
        this.rerankBeforeChunkIds = rerankBeforeChunkIds;
    }

    public List<Long> getRerankAfterChunkIds() {
        return rerankAfterChunkIds;
    }

    public void setRerankAfterChunkIds(List<Long> rerankAfterChunkIds) {
        this.rerankAfterChunkIds = rerankAfterChunkIds;
    }

    public List<AgentMemoryItem> getMemoriesUsed() {
        return memoriesUsed;
    }

    public void setMemoriesUsed(List<AgentMemoryItem> memoriesUsed) {
        this.memoriesUsed = memoriesUsed;
    }

    public int getMemoriesQueried() {
        return memoriesQueried;
    }

    public void setMemoriesQueried(int memoriesQueried) {
        this.memoriesQueried = memoriesQueried;
    }

    public String getMemoryMatchReason() {
        return memoryMatchReason;
    }

    public void setMemoryMatchReason(String memoryMatchReason) {
        this.memoryMatchReason = memoryMatchReason;
    }

    public String getAnswerProvider() {
        return answerProvider;
    }

    public void setAnswerProvider(String answerProvider) {
        this.answerProvider = answerProvider;
    }

    public String getAnswerModelName() {
        return answerModelName;
    }

    public void setAnswerModelName(String answerModelName) {
        this.answerModelName = answerModelName;
    }

    public Long getAnswerLatencyMs() {
        return answerLatencyMs;
    }

    public void setAnswerLatencyMs(Long answerLatencyMs) {
        this.answerLatencyMs = answerLatencyMs;
    }

    public boolean isAnswerFallbackUsed() {
        return answerFallbackUsed;
    }

    public void setAnswerFallbackUsed(boolean answerFallbackUsed) {
        this.answerFallbackUsed = answerFallbackUsed;
    }

    public String getAnswerErrorMessage() {
        return answerErrorMessage;
    }

    public void setAnswerErrorMessage(String answerErrorMessage) {
        this.answerErrorMessage = answerErrorMessage;
    }
}
