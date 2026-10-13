package com.example.rag.service.impl;

import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunSummary;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@EnabledIfEnvironmentVariable(named = "RAG_E2E_ENABLED", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "answer.mode=rule"})
class EvalHistoryEndToEndTest {

    private static final Path OUTPUT_DIR = createOutputDirectory();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EvalHistoryService historyService;

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

        ResponseEntity<JsonNode> gate = restTemplate.getForEntity(
                "/eval/gate?base=" + firstId + "&candidate=" + secondId, JsonNode.class);
        assertEquals(OK, gate.getStatusCode());
        assertEquals(firstId, gate.getBody().path("baseRunId").asLong());
        assertEquals(secondId, gate.getBody().path("candidateRunId").asLong());
        assertEquals(comparison.getBody().path("graphRegressions").asInt(),
                gate.getBody().path("observed").path("graphRegressions").asInt());
        assertTrue(gate.getBody().has("passed"));

        ResponseEntity<JsonNode> page = restTemplate.getForEntity("/eval/runs?limit=1", JsonNode.class);
        assertEquals(OK, page.getStatusCode());
        assertEquals(secondId, page.getBody().path("items").get(0).path("runId").asLong());
        assertEquals(secondId, page.getBody().path("nextBefore").asLong());
        ResponseEntity<JsonNode> older = restTemplate.getForEntity(
                "/eval/runs?limit=1&before=" + secondId, JsonNode.class);
        assertEquals(OK, older.getStatusCode());
        assertEquals(firstId, older.getBody().path("items").get(0).path("runId").asLong());

        ResponseEntity<JsonNode> firstCasePage = restTemplate.getForEntity(
                "/eval/runs/" + firstId + "/cases?limit=1", JsonNode.class);
        assertEquals(OK, firstCasePage.getStatusCode());
        assertEquals(0, firstCasePage.getBody().path("nextAfter").asInt());
        ResponseEntity<JsonNode> secondCasePage = restTemplate.getForEntity(
                "/eval/runs/" + firstId + "/cases?limit=1&after=0", JsonNode.class);
        assertEquals(OK, secondCasePage.getStatusCode());
        assertEquals("EVAL-002", secondCasePage.getBody().path("items").get(0).path("caseId").asText());

        ResponseEntity<JsonNode> originalAfterSecondRun = restTemplate.getForEntity(
                "/eval/runs/" + firstId + "/cases?limit=20", JsonNode.class);
        assertEquals(cases.getBody(), originalAfterSecondRun.getBody(),
                "a later run must not rewrite earlier case snapshots");
    }

    @Test
    void historyRejectsInvalidRequestsAndIncompleteComparisons() {
        assertEquals(BAD_REQUEST, restTemplate.getForEntity("/eval/runs?limit=0", JsonNode.class).getStatusCode());
        assertEquals(BAD_REQUEST, restTemplate.getForEntity(
                "/eval/gate?base=1&candidate=2&maxNewWrongAnswers=-1",
                JsonNode.class).getStatusCode());
        assertEquals(BAD_REQUEST, restTemplate.getForEntity("/eval/runs?before=-1", JsonNode.class).getStatusCode());
        assertEquals(BAD_REQUEST, restTemplate.getForEntity("/eval/runs/-1", JsonNode.class).getStatusCode());
        assertEquals(NOT_FOUND, restTemplate.getForEntity("/eval/runs/999999999", JsonNode.class).getStatusCode());

        long failedId = historyService.begin("a".repeat(64), 1);
        historyService.fail(failedId, new IllegalStateException("private details should not leak"));
        ResponseEntity<JsonNode> failed = restTemplate.getForEntity("/eval/runs/" + failedId, JsonNode.class);
        assertEquals(OK, failed.getStatusCode());
        assertEquals("FAILED", failed.getBody().path("status").asText());
        assertEquals("Evaluation failed: IllegalStateException", failed.getBody().path("errorMessage").asText());
        assertEquals(CONFLICT, restTemplate.getForEntity(
                "/eval/runs/" + failedId + "/cases", JsonNode.class).getStatusCode());
        assertEquals(CONFLICT, restTemplate.getForEntity(
                "/eval/compare?base=" + failedId + "&candidate=" + failedId,
                JsonNode.class).getStatusCode());

        long otherId = historyService.begin("b".repeat(64), 1);
        EvalRunResult caseResult = new EvalRunResult();
        caseResult.setId("ONE-CASE");
        EvalRunSummary summary = new EvalRunSummary();
        summary.setRunId(otherId);
        summary.setTotal(1);
        summary.setResults(java.util.List.of(caseResult));
        historyService.complete(otherId, summary);
        assertThrows(IllegalStateException.class, () -> historyService.complete(otherId, summary),
                "completed runs must not accept replacement snapshots");
        long mismatchedId = historyService.begin("c".repeat(64), 1);
        EvalRunSummary mismatched = new EvalRunSummary();
        mismatched.setRunId(mismatchedId);
        mismatched.setTotal(1);
        mismatched.setResults(java.util.List.of(caseResult));
        historyService.complete(mismatchedId, mismatched);
        assertEquals(CONFLICT, restTemplate.getForEntity(
                "/eval/compare?base=" + otherId + "&candidate=" + mismatchedId,
                JsonNode.class).getStatusCode());
        assertEquals(BAD_REQUEST, restTemplate.getForEntity(
                "/eval/runs/" + otherId + "/cases?limit=101", JsonNode.class).getStatusCode());
    }

    private static Path createOutputDirectory() {
        try {
            return Files.createTempDirectory("agent-rag-history-eval-");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create isolated eval output directory", ex);
        }
    }
}
