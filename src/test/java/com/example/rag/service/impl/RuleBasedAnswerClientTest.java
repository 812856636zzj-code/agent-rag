package com.example.rag.service.impl;

import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleBasedAnswerClientTest {

    @Test
    void generateReturnsRuleProvider() {
        RuleBasedAnswerClient client = new RuleBasedAnswerClient();
        LlmAnswerRequest request = new LlmAnswerRequest();
        request.setRuleBasedAnswer("rule answer");

        LlmAnswerResult result = client.generate(request);

        assertEquals("RULE", result.getProvider());
        assertEquals("rule-based-assembler", result.getModelName());
        assertEquals("rule answer", result.getAnswer());
        assertFalse(result.isFallbackUsed());
    }
}
