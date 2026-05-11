package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class Day6AgentToolEvalResult {

    private String id;
    private String question;
    private String questionType;
    private List<String> expectedTools = new ArrayList<>();
    private String routerOnlyAnswer;
    private String toolAgentAnswer;
    private boolean routerOnlyHit;
    private boolean toolAgentHit;
    private boolean graphHit;
    private boolean sqlSearchHit;
    private boolean chunkSearchHit;
    private boolean traceHit;
    private boolean sourceHit;
    private boolean evidenceHit;
    private boolean expectedToolsHit;
    private String notes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public List<String> getExpectedTools() {
        return expectedTools;
    }

    public void setExpectedTools(List<String> expectedTools) {
        this.expectedTools = expectedTools;
    }

    public String getRouterOnlyAnswer() {
        return routerOnlyAnswer;
    }

    public void setRouterOnlyAnswer(String routerOnlyAnswer) {
        this.routerOnlyAnswer = routerOnlyAnswer;
    }

    public String getToolAgentAnswer() {
        return toolAgentAnswer;
    }

    public void setToolAgentAnswer(String toolAgentAnswer) {
        this.toolAgentAnswer = toolAgentAnswer;
    }

    public boolean isRouterOnlyHit() {
        return routerOnlyHit;
    }

    public void setRouterOnlyHit(boolean routerOnlyHit) {
        this.routerOnlyHit = routerOnlyHit;
    }

    public boolean isToolAgentHit() {
        return toolAgentHit;
    }

    public void setToolAgentHit(boolean toolAgentHit) {
        this.toolAgentHit = toolAgentHit;
    }

    public boolean isGraphHit() {
        return graphHit;
    }

    public void setGraphHit(boolean graphHit) {
        this.graphHit = graphHit;
    }

    public boolean isSqlSearchHit() {
        return sqlSearchHit;
    }

    public void setSqlSearchHit(boolean sqlSearchHit) {
        this.sqlSearchHit = sqlSearchHit;
    }

    public boolean isChunkSearchHit() {
        return chunkSearchHit;
    }

    public void setChunkSearchHit(boolean chunkSearchHit) {
        this.chunkSearchHit = chunkSearchHit;
    }

    public boolean isTraceHit() {
        return traceHit;
    }

    public void setTraceHit(boolean traceHit) {
        this.traceHit = traceHit;
    }

    public boolean isSourceHit() {
        return sourceHit;
    }

    public void setSourceHit(boolean sourceHit) {
        this.sourceHit = sourceHit;
    }

    public boolean isEvidenceHit() {
        return evidenceHit;
    }

    public void setEvidenceHit(boolean evidenceHit) {
        this.evidenceHit = evidenceHit;
    }

    public boolean isExpectedToolsHit() {
        return expectedToolsHit;
    }

    public void setExpectedToolsHit(boolean expectedToolsHit) {
        this.expectedToolsHit = expectedToolsHit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
