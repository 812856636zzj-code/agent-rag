package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class Day7EvalQuestion {

    private String id;
    private String question;
    private String expectedAnswer;
    private String category;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedEntities = new ArrayList<>();
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

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public List<String> getExpectedEntities() {
        return expectedEntities;
    }

    public void setExpectedEntities(List<String> expectedEntities) {
        this.expectedEntities = expectedEntities;
    }

    public List<String> getExpectedRelations() {
        return expectedRelations;
    }

    public void setExpectedRelations(List<String> expectedRelations) {
        this.expectedRelations = expectedRelations;
    }
}
