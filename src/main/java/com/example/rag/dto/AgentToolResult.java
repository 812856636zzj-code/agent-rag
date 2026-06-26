package com.example.rag.dto;

import com.example.rag.enums.ToolType;

public class AgentToolResult {

    private ToolType toolType;
    private boolean success;
    private String summary;
    private Object data;
    private String errorMessage;
    private Long latencyMs;
    private String answerProvider;
    private String answerModelName;
    private boolean answerFallbackUsed;

    public ToolType getToolType() {
        return toolType;
    }

    public void setToolType(ToolType toolType) {
        this.toolType = toolType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
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

    public boolean isAnswerFallbackUsed() {
        return answerFallbackUsed;
    }

    public void setAnswerFallbackUsed(boolean answerFallbackUsed) {
        this.answerFallbackUsed = answerFallbackUsed;
    }
}
