package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AgentExecutionTrace {

    private String traceId;
    private List<AgentExecutionStep> steps = new ArrayList<>();
    private int memoriesQueried;
    private List<AgentMemoryItem> memoriesUsed = new ArrayList<>();
    private String memoryMatchReason;
    private String answerProvider;
    private String answerModelName;
    private Long answerLatencyMs;
    private boolean answerFallbackUsed;
    private String answerErrorMessage;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<AgentExecutionStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentExecutionStep> steps) {
        this.steps = steps;
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
