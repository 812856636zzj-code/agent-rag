package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AgentExecutionTrace {

    private String traceId;
    private List<AgentExecutionStep> steps = new ArrayList<>();
    private int memoriesQueried;
    private List<AgentMemoryItem> memoriesUsed = new ArrayList<>();
    private String memoryMatchReason;

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
}
