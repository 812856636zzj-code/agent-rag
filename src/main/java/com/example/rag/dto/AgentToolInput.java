package com.example.rag.dto;

import com.example.rag.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;

public class AgentToolInput {

    private String question;
    private QuestionType questionType;
    private AgentRouteDecision routeDecision;
    private List<RelationHit> relationHits = new ArrayList<>();
    private List<ChunkHit> chunkHits = new ArrayList<>();
    private QueryRewriteResult rewriteResult;
    private RerankResult rerankResult;
    private List<String> matchedQueries = new ArrayList<>();
    private boolean recallFallbackUsed;
    private java.util.Map<String, Integer> candidateCountByQuery = new java.util.LinkedHashMap<>();
    private int candidateCountAfterMerge;
    private int duplicateCandidateRemovedCount;
    private Object context;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public AgentRouteDecision getRouteDecision() {
        return routeDecision;
    }

    public void setRouteDecision(AgentRouteDecision routeDecision) {
        this.routeDecision = routeDecision;
    }

    public List<RelationHit> getRelationHits() {
        return relationHits;
    }

    public void setRelationHits(List<RelationHit> relationHits) {
        this.relationHits = relationHits;
    }

    public List<ChunkHit> getChunkHits() {
        return chunkHits;
    }

    public void setChunkHits(List<ChunkHit> chunkHits) {
        this.chunkHits = chunkHits;
    }

    public QueryRewriteResult getRewriteResult() {
        return rewriteResult;
    }

    public void setRewriteResult(QueryRewriteResult rewriteResult) {
        this.rewriteResult = rewriteResult;
    }

    public RerankResult getRerankResult() {
        return rerankResult;
    }

    public void setRerankResult(RerankResult rerankResult) {
        this.rerankResult = rerankResult;
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

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
