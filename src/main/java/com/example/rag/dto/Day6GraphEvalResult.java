package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class Day6GraphEvalResult {

    private String id;
    private String question;
    private String questionType;
    private String expectedMode;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedRelations = new ArrayList<>();
    private String baselineAnswer;
    private String graphAnswer;
    private boolean baselineHit;
    private boolean graphHit;
    private boolean relationHit;
    private boolean sourceHit;
    private boolean evidenceHit;
    private boolean expectedModeHit;
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

    public String getExpectedMode() {
        return expectedMode;
    }

    public void setExpectedMode(String expectedMode) {
        this.expectedMode = expectedMode;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public List<String> getExpectedRelations() {
        return expectedRelations;
    }

    public void setExpectedRelations(List<String> expectedRelations) {
        this.expectedRelations = expectedRelations;
    }

    public String getBaselineAnswer() {
        return baselineAnswer;
    }

    public void setBaselineAnswer(String baselineAnswer) {
        this.baselineAnswer = baselineAnswer;
    }

    public String getGraphAnswer() {
        return graphAnswer;
    }

    public void setGraphAnswer(String graphAnswer) {
        this.graphAnswer = graphAnswer;
    }

    public boolean isBaselineHit() {
        return baselineHit;
    }

    public void setBaselineHit(boolean baselineHit) {
        this.baselineHit = baselineHit;
    }

    public boolean isGraphHit() {
        return graphHit;
    }

    public void setGraphHit(boolean graphHit) {
        this.graphHit = graphHit;
    }

    public boolean isRelationHit() {
        return relationHit;
    }

    public void setRelationHit(boolean relationHit) {
        this.relationHit = relationHit;
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

    public boolean isExpectedModeHit() {
        return expectedModeHit;
    }

    public void setExpectedModeHit(boolean expectedModeHit) {
        this.expectedModeHit = expectedModeHit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
