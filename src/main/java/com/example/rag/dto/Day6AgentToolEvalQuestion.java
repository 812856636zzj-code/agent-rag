package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class Day6AgentToolEvalQuestion {

    private String id;
    private String question;
    private String questionType;
    private String expectedMode;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedTools = new ArrayList<>();
    private List<String> expectedSourceTypes = new ArrayList<>();
    private List<String> expectedRelations = new ArrayList<>();
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

    public List<String> getExpectedTools() {
        return expectedTools;
    }

    public void setExpectedTools(List<String> expectedTools) {
        this.expectedTools = expectedTools;
    }

    public List<String> getExpectedSourceTypes() {
        return expectedSourceTypes;
    }

    public void setExpectedSourceTypes(List<String> expectedSourceTypes) {
        this.expectedSourceTypes = expectedSourceTypes;
    }

    public List<String> getExpectedRelations() {
        return expectedRelations;
    }

    public void setExpectedRelations(List<String> expectedRelations) {
        this.expectedRelations = expectedRelations;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
