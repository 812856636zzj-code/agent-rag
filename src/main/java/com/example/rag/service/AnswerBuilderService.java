package com.example.rag.service;

import com.example.rag.dto.AskContext;

public interface AnswerBuilderService {

    String buildAnswer(AskContext context);
}
