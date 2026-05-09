package com.example.rag.dto;

public class Day7EvalResult {

    private String id;
    private String question;
    private String expectedAnswer;
    private String category;
    private String baselineAnswer;
    private String graphAnswer;
    private boolean baselineHit;
    private boolean graphHit;
    private int baselineReadabilityScore;
    private int graphReadabilityScore;
    private boolean baselineNoise;
    private boolean graphNoise;
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

    public int getBaselineReadabilityScore() {
        return baselineReadabilityScore;
    }

    public void setBaselineReadabilityScore(int baselineReadabilityScore) {
        this.baselineReadabilityScore = baselineReadabilityScore;
    }

    public int getGraphReadabilityScore() {
        return graphReadabilityScore;
    }

    public void setGraphReadabilityScore(int graphReadabilityScore) {
        this.graphReadabilityScore = graphReadabilityScore;
    }

    public boolean isBaselineNoise() {
        return baselineNoise;
    }

    public void setBaselineNoise(boolean baselineNoise) {
        this.baselineNoise = baselineNoise;
    }

    public boolean isGraphNoise() {
        return graphNoise;
    }

    public void setGraphNoise(boolean graphNoise) {
        this.graphNoise = graphNoise;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
