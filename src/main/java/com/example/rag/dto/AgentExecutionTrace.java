package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AgentExecutionTrace {

    private String traceId;
    private List<AgentExecutionStep> steps = new ArrayList<>();

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
}
