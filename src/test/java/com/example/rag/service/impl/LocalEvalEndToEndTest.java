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
        for (JsonNode result : results) {
            assertTrue(result.has("wrongAnswer"));
            assertTrue(result.has("citationMatched"));
            assertTrue(result.has("citationMissing"));
            assertTrue(result.has("abstained"));
        }
    }

    private static Path createOutputDirectory() {
        try {
            return Files.createTempDirectory("agent-rag-eval-");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create isolated eval output directory", ex);
        }
    }
}
