package com.example.rag.service;

import com.example.rag.dto.GraphAskResponse;

public interface AskService {

    GraphAskResponse ask(String question);

    GraphAskResponse ask(String question, String mode);

    void saveQueryLog(String question, String answer, Long sourceDocId, long responseTimeMs);
}
