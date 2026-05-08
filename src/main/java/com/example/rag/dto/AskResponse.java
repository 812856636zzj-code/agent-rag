package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class AskResponse {

    private String question;
    private String keyword;
    private List<ChunkResponse> chunks = new ArrayList<>();
    private String structuredContext;
    private String answer;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<ChunkResponse> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkResponse> chunks) {
        this.chunks = chunks;
    }

    public String getStructuredContext() {
        return structuredContext;
    }

    public void setStructuredContext(String structuredContext) {
        this.structuredContext = structuredContext;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
