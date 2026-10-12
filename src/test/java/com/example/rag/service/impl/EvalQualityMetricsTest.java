package com.example.rag.service.impl;

import com.example.rag.dto.AskResponse;
import com.example.rag.dto.EvalRunQuestion;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunSummary;
import com.example.rag.dto.SourceItem;
import com.example.rag.service.AgentExecutor;
import com.example.rag.service.AgentRouterService;
import com.example.rag.service.AskService;
import com.example.rag.service.MemoryService;
import com.example.rag.service.QuestionClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvalQualityMetricsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AskService askService = mock(AskService.class);
    private final EvalServiceImpl evalService = new EvalServiceImpl(
            askService,
            mock(QuestionClassifier.class),
            mock(AgentRouterService.class),
            mock(AgentExecutor.class),
            mock(MemoryService.class),
            objectMapper
    );

    @Test
    void distinguishesWrongAnswerFromKeywordsOnlyInRetrievedContent() {
        EvalRunQuestion question = question("wrong", "ORDERS fields?");
        AskResponse graph = response("USERS has NAME", "ORDERS AMOUNT", "users.md",
                "ORDERS AMOUNT", "USERS has NAME");

        EvalRunResult result = evaluate(question, graph);
        JsonNode json = objectMapper.valueToTree(result);

        assertTrue(result.isGraphHit(), "The legacy hit includes retrieved content");
        assertTrue(result.isSourceHit(), "The legacy source hit includes raw source content");
        assertTrue(json.path("wrongAnswer").asBoolean(), "The answer text itself misses the expected facts");
        assertFalse(json.path("citationMatched").asBoolean(), "Unrelated evidence is not a valid citation");
        assertTrue(json.path("citationMissing").asBoolean());
    }

    @Test
    void countsGroundedAnswersAndAbstentionsSeparatelyInReport() {
        EvalRunResult grounded = evaluate(
                question("grounded", "ORDERS fields?"),
                response("ORDERS has AMOUNT", "", "schema.md", "ORDERS has AMOUNT", "ORDERS has AMOUNT")
        );
        AskResponse abstention = new AskResponse();
        abstention.setAnswer(AgentAnswerAssembler.NO_ANSWER);
        abstention.setSources(new ArrayList<>());
        EvalRunResult abstained = evaluate(question("abstained", "ORDERS fields?"), abstention);
        EvalRunResult wrong = evaluate(
                question("wrong", "ORDERS fields?"),
                response("USERS has NAME", "ORDERS AMOUNT", "users.md", "ORDERS AMOUNT", "USERS has NAME")
        );

        JsonNode groundedJson = objectMapper.valueToTree(grounded);
        JsonNode abstainedJson = objectMapper.valueToTree(abstained);
        assertTrue(groundedJson.path("citationMatched").asBoolean());
        assertFalse(groundedJson.path("wrongAnswer").asBoolean());
        assertTrue(abstainedJson.path("abstained").asBoolean());
        assertFalse(abstainedJson.path("wrongAnswer").asBoolean());
        assertFalse(abstainedJson.path("citationMissing").asBoolean());

        EvalRunSummary summary = ReflectionTestUtils.invokeMethod(
                evalService, "buildEvalRunSummary", List.of(grounded, abstained, wrong)
        );
        JsonNode summaryJson = objectMapper.valueToTree(summary);
        assertEquals(1, summaryJson.path("wrongAnswerCount").asInt());
        assertEquals(1, summaryJson.path("citationMatchedCount").asInt());
        assertEquals(1, summaryJson.path("citationMissingCount").asInt());
        assertEquals(1, summaryJson.path("abstainedCount").asInt());

        String report = ReflectionTestUtils.invokeMethod(evalService, "buildEvalRunReport", summary);
        assertTrue(report.contains("wrongAnswerCount: 1"));
        assertTrue(report.contains("citationMatchedCount: 1"));
        assertTrue(report.contains("| WrongAnswer | CitationMatched |"));
    }

    private EvalRunResult evaluate(EvalRunQuestion question, AskResponse graph) {
        AskResponse baseline = new AskResponse();
        baseline.setAnswer("baseline unavailable");
        when(askService.ask(question.getQuestion(), "baseline")).thenReturn(baseline);
        when(askService.ask(question.getQuestion(), "graph")).thenReturn(graph);
        return ReflectionTestUtils.invokeMethod(evalService, "runEvalQuestion", question);
    }

    private EvalRunQuestion question(String id, String text) {
        EvalRunQuestion question = new EvalRunQuestion();
        question.setId(id);
        question.setQuestion(text);
        question.setType("entity");
        question.setExpectedKeywords(List.of("ORDERS", "AMOUNT"));
        question.setExpectedSource(List.of("ORDERS"));
        return question;
    }

    private AskResponse response(String answer, String context, String documentName,
                                 String sourceContent, String evidence) {
        AskResponse response = new AskResponse();
        response.setAnswer(answer);
        response.setStructuredContext(context);
        SourceItem source = new SourceItem();
        source.setDocumentName(documentName);
        source.setContent(sourceContent);
        source.setEvidenceSentences(List.of(evidence));
        response.setSources(List.of(source));
        return response;
    }
}
