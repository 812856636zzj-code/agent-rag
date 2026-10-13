package com.example.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class LocalEvalEndToEndTest {

    private static final Path OUTPUT_DIR = createOutputDirectory();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void outputDirectory(DynamicPropertyRegistry registry) {
        registry.add("rag.eval.output-dir", OUTPUT_DIR::toString);
    }

    @Test
    void fixedEvalRunsAgainstPreparedOracleAndWritesIsolatedReports() throws IOException {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity("/eval/run", null, JsonNode.class);

        assertEquals(OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(15, response.getBody().path("total").asInt());
        assertTrue(Files.isRegularFile(OUTPUT_DIR.resolve("latest_eval_results.json")));
        assertTrue(Files.isRegularFile(OUTPUT_DIR.resolve("latest_eval_report.md")));

        JsonNode results = objectMapper.readTree(OUTPUT_DIR.resolve("latest_eval_results.json").toFile());
        assertEquals(15, results.size());
        boolean fixtureAnswered = false;
        int wrongAnswers = 0;
        int citationMatched = 0;
        int citationMissing = 0;
        int abstained = 0;
        for (JsonNode result : results) {
            assertTrue(result.has("wrongAnswer"));
            assertTrue(result.has("citationMatched"));
            assertTrue(result.has("citationMissing"));
            assertTrue(result.has("abstained"));
            wrongAnswers += result.path("wrongAnswer").asBoolean() ? 1 : 0;
            citationMatched += result.path("citationMatched").asBoolean() ? 1 : 0;
            citationMissing += result.path("citationMissing").asBoolean() ? 1 : 0;
            abstained += result.path("abstained").asBoolean() ? 1 : 0;
            if ("EVAL-005".equals(result.path("id").asText())) {
                fixtureAnswered = result.path("graphHit").asBoolean()
                        && result.path("sourceHit").asBoolean()
                        && result.path("graphAnswer").asText().contains("EMBEDDING_STATUS");
            }
        }
        assertTrue(fixtureAnswered, "seeded RAG_DOCUMENT_CHUNKS document must support EVAL-005");
        assertEquals(wrongAnswers, response.getBody().path("wrongAnswerCount").asInt());
        assertEquals(citationMatched, response.getBody().path("citationMatchedCount").asInt());
        assertEquals(citationMissing, response.getBody().path("citationMissingCount").asInt());
        assertEquals(abstained, response.getBody().path("abstainedCount").asInt());
    }

    private static Path createOutputDirectory() {
        try {
            return Files.createTempDirectory("agent-rag-eval-");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create isolated eval output directory", ex);
        }
    }
}
