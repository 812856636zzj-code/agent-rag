package com.example.rag.service;

import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;

public interface LlmAnswerClient {

    LlmAnswerResult generate(LlmAnswerRequest request);
}
