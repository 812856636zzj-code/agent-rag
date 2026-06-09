package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class EvalRunQuestion {

    private String id;
    private String question;
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedSource = new ArrayList<>();
    private String type;

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

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public List<String> getExpectedSource() {
        return expectedSource;
    }

    public void setExpectedSource(List<String> expectedSource) {
        this.expectedSource = expectedSource;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
