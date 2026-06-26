package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jAnswerClientTest {

    @Test
    void blankApiKeyFallsBackToRuleClient() {
        AnswerGenerationProperties properties = new AnswerGenerationProperties();
        properties.setMode("langchain4j");
        properties.getLangchain4j().setEnabled(true);
        properties.getLangchain4j().setApiKey("");
        properties.getLangchain4j().setFallbackEnabled(true);

        LangChain4jAnswerClient client = new LangChain4jAnswerClient(properties, new RuleBasedAnswerClient());
        LlmAnswerRequest request = new LlmAnswerRequest();
        request.setRuleBasedAnswer("fallback answer");

        LlmAnswerResult result = client.generate(request);

        assertEquals("RULE", result.getProvider());
        assertEquals("rule-based-assembler", result.getModelName());
        assertEquals("fallback answer", result.getAnswer());
        assertTrue(result.isFallbackUsed());
        assertEquals("langchain4j apiKey is blank", result.getErrorMessage());
    }
}
