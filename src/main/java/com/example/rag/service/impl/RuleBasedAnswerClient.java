package com.example.rag.service.impl;

import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.service.LlmAnswerClient;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedAnswerClient implements LlmAnswerClient {

    @Override
    public LlmAnswerResult generate(LlmAnswerRequest request) {
        long start = System.currentTimeMillis();
        LlmAnswerResult result = new LlmAnswerResult();
        result.setAnswer(request == null ? "" : request.getRuleBasedAnswer());
        result.setProvider("RULE");
        result.setModelName("rule-based-assembler");
        result.setLatencyMs(System.currentTimeMillis() - start);
        result.setFallbackUsed(false);
        return result;
    }
}
