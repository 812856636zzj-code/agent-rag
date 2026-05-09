package com.example.rag.dto;

public class ScoredSentence {

    private final String text;
    private final int score;

    public ScoredSentence(String text, int score) {
        this.text = text;
        this.score = score;
    }

    public String getText() {
        return text;
    }

    public int getScore() {
        return score;
    }
}
