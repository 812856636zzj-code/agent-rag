package com.example.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;

@EnabledIfEnvironmentVariable(named = "RAG_E2E_ENABLED", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "answer.mode=rule"})
class EvalHistoryEndToEndTest {

    private static final Path OUTPUT_DIR = createOutputDirectory();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void outputDirectory(DynamicPropertyRegistry registry) {
        registry.add("rag.eval.output-dir", OUTPUT_DIR::toString);
    }

    @Test
    void fixedEvalPersistsAndComparesTwoRuns() {
        ResponseEntity<JsonNode> first = restTemplate.postForEntity("/eval/run", null, JsonNode.class);
        assertEquals(OK, first.getStatusCode());
        assertNotNull(first.getBody());
        long firstId = first.getBody().path("runId").asLong();
        assertTrue(firstId > 0, "a completed eval must have a persisted run ID");

        ResponseEntity<JsonNode> snapshot = restTemplate.getForEntity("/eval/runs/" + firstId, JsonNode.class);
        assertEquals(OK, snapshot.getStatusCode());
        assertNotNull(snapshot.getBody());
        assertEquals(firstId, snapshot.getBody().path("runId").asLong());
        assertEquals(15, snapshot.getBody().path("total").asInt());

        ResponseEntity<JsonNode> cases = restTemplate.getForEntity(
                "/eval/runs/" + firstId + "/cases?limit=20", JsonNode.class);
        assertEquals(OK, cases.getStatusCode());
        assertNotNull(cases.getBody());
        assertEquals(15, cases.getBody().path("items").size());
        assertEquals("EVAL-001", cases.getBody().path("items").get(0).path("caseId").asText());

        ResponseEntity<JsonNode> second = restTemplate.postForEntity("/eval/run", null, JsonNode.class);
        assertEquals(OK, second.getStatusCode());
        assertNotNull(second.getBody());
        long secondId = second.getBody().path("runId").asLong();
        assertTrue(secondId > firstId);

        ResponseEntity<JsonNode> comparison = restTemplate.getForEntity(
                "/eval/compare?base=" + firstId + "&candidate=" + secondId, JsonNode.class);
        assertEquals(OK, comparison.getStatusCode());
        assertNotNull(comparison.getBody());
        assertEquals(firstId, comparison.getBody().path("baseRunId").asLong());
        assertEquals(secondId, comparison.getBody().path("candidateRunId").asLong());
        assertEquals(15, comparison.getBody().path("caseCount").asInt());
        assertEquals(15, comparison.getBody().path("cases").size());
    }

    private static Path createOutputDirectory() {
        try {
            return Files.createTempDirectory("agent-rag-history-eval-");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create isolated eval output directory", ex);
        }
    }
}
