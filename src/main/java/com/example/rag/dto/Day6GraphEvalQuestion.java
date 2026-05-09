package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class Day6GraphEvalQuestion {

    private String id;
    private String question;
    private String questionType;
    private String expectedMode;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedRelations = new ArrayList<>();

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
}
