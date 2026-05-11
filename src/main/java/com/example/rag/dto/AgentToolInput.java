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

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
