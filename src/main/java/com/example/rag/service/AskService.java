package com.example.rag.service;

import com.example.rag.dto.GraphAskResponse;

public interface AskService {

    GraphAskResponse ask(String question);

    void saveQueryLog(String question, String answer, Long sourceDocId, long responseTimeMs);
}
