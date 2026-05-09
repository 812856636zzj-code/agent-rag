package com.example.rag.service;

import com.example.rag.dto.AskResponse;

public interface AskService {

    AskResponse ask(String question);

    AskResponse ask(String question, String mode);

    void saveQueryLog(String question, String answer, Long sourceDocId, long responseTimeMs);
}
