package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.RelationPathItem;
import com.example.rag.dto.SourceItem;
import com.example.rag.dto.SqlTableColumnResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportedEvidenceBoundaryTest {

    @Test
    void modelRequestExcludesUnsupportedChunksAndStructuredContext() throws Exception {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("What does the supported document say?");
        input.setChunkHits(new ArrayList<>(List.of(
                chunk(11L, 21L, "Supported fact. Hidden claim in the same chunk."),
                chunk(12L, 22L, "Unrelated raw claim."))));

        AskResponse assembled = answer("Preassembled hidden claim.",
                source("CHUNK", 11L, 21L, "Supported fact. Hidden claim in the same chunk.", "Supported fact."),
                source("CHUNK", 12L, 22L, "Unrelated raw claim.", " "));
        assembled.setStructuredContext("Supported fact. Unrelated raw claim.");
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);
        LangChain4jAnswerClient model = modelReturning("Supported fact.");

        AgentToolResult result = tool(assembler, model, true).execute(input);
        LlmAnswerRequest request = capturedRequest(model);

        assertTrue(result.isSuccess());
        assertEquals(1, request.getTopChunks().size());
        assertEquals("Supported fact.", request.getTopChunks().get(0).getContent());
        assertTrue(request.getRelationEvidence().isEmpty());
        assertFalse(request.getStructuredContext().contains("Unrelated raw claim"));
        assertFalse(request.getRuleBasedAnswer().contains("hidden claim"));
        assertEquals(1, request.getSources().size());
        assertEquals("Supported fact.", request.getSources().get(0).getContent());
        String prompt = promptFor(request);
        assertTrue(prompt.contains("Supported fact."));
        assertFalse(prompt.contains("Hidden claim"));
        assertFalse(prompt.contains("Unrelated raw claim"));
    }

    @Test
    void ruleFallbackRegeneratesAnswerFromSupportedEvidence() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("What does the supported document say?");
        input.setChunkHits(List.of(chunk(11L, 21L, "Supported fact. Hidden claim.")));
        AskResponse assembled = answer("Preassembled hidden claim.",
                source("CHUNK", 11L, 21L, "Supported fact. Hidden claim.", "Supported fact."));
        assembled.setStructuredContext("Hidden claim.");
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AgentToolResult result = tool(assembler, mock(LangChain4jAnswerClient.class), false).execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertTrue(response.getAnswer().contains("Supported fact."));
        assertFalse(response.getAnswer().contains("hidden claim"));
        assertFalse(response.getStructuredContext().contains("Hidden claim"));
        assertEquals("Supported fact.", response.getSources().get(0).getContent());
    }

    @Test
    void blankModelAnswerCannotRestorePreassembledUnsupportedAnswer() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("What is supported?");
        input.setChunkHits(List.of(chunk(11L, 21L, "Supported fact. Hidden claim.")));
        AskResponse assembled = answer("Preassembled hidden claim.",
                source("CHUNK", 11L, 21L, "Supported fact. Hidden claim.", "Supported fact."));
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AgentToolResult result = tool(assembler, modelReturning(""), true).execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertEquals("Supported fact.", response.getAnswer());
    }

    @Test
    void relationEvidenceAndUnrelatedChunkAreRemovedBeforeModelCall() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("What relation is supported?");
        RelationHit supported = relation(31L, 41L, "Supported relation. Hidden relation claim.");
        RelationHit unsupported = relation(32L, 42L, "Unrelated relation claim.");
        input.setRelationHits(new ArrayList<>(List.of(supported, unsupported)));
        input.setChunkHits(List.of(chunk(90L, 91L, "Unrelated chunk claim.")));

        AskResponse assembled = answer("Preassembled unrelated relation claim.",
                source("RELATION", 31L, 41L, supported.getEvidenceText(), "Supported relation."),
                source("RELATION", 32L, 42L, unsupported.getEvidenceText(), ""));
        assembled.setStructuredContext("Unrelated relation claim.");
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildRelationResponse(input.getQuestion(), input.getRelationHits())).thenReturn(assembled);
        LangChain4jAnswerClient model = modelReturning("Supported relation.");

        AgentToolResult result = tool(assembler, model, true).execute(input);
        LlmAnswerRequest request = capturedRequest(model);

        assertTrue(result.isSuccess());
        assertTrue(request.getTopChunks().isEmpty());
        assertEquals(1, request.getRelationEvidence().size());
        assertEquals("Supported relation.", request.getRelationEvidence().get(0).getEvidenceText());
        assertFalse(request.getStructuredContext().contains("Unrelated relation claim"));
    }

    @Test
    void relationPathsRequireMatchingProvenanceAndSupportedEntities() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("How is TABLE_A related to TABLE_B?");
        RelationHit supported = relation(31L, 41L, "TABLE_A relates to TABLE_B. Hidden claim.");
        RelationHit unsupported = relation(32L, 42L, "TABLE_X relates to TABLE_Y.");
        input.setRelationHits(List.of(supported, unsupported));
        AskResponse assembled = answer("Hidden claim.",
                source("RELATION", 31L, 41L, supported.getEvidenceText(), "TABLE_A relates to TABLE_B."),
                source("RELATION", 32L, 42L, unsupported.getEvidenceText(), ""));
        assembled.setRelationPaths(List.of(path(31L, 41L, "TABLE_A", "TABLE_B", "Hidden claim."),
                path(32L, 42L, "TABLE_X", "TABLE_Y", "Unrelated claim."),
                path(31L, 99L, "TABLE_A", "TABLE_B", "Wrong document.")));
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildRelationResponse(input.getQuestion(), input.getRelationHits())).thenReturn(assembled);

        AgentToolResult result = tool(assembler, modelReturning("TABLE_A relates to TABLE_B."), true)
                .execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertEquals(1, response.getRelationPaths().size());
        assertEquals("TABLE_A relates to TABLE_B.", response.getRelationPaths().get(0).getEvidenceText());
        assertEquals("TABLE_A relates to TABLE_B.", response.getRelationPaths().get(0).getSummary());
    }

    @Test
    void sqlMetadataUsesOnlyItsSelectedEvidence() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("Which columns does ORDERS contain?");
        SqlTableColumnResult sql = new SqlTableColumnResult();
        input.setContext(sql);
        AskResponse assembled = answer("Preassembled unrelated column.",
                source("SQL_METADATA", null, null,
                        "USER_TAB_COLUMNS ORDERS -> ORDER_ID. Unrelated column.",
                        "USER_TAB_COLUMNS ORDERS -> ORDER_ID."));
        assembled.setStructuredContext("Unrelated column.");
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        when(assembler.buildSqlMetadataResponse(input.getQuestion(), sql)).thenReturn(assembled);

        AgentToolResult result = tool(assembler, mock(LangChain4jAnswerClient.class), false).execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertEquals("USER_TAB_COLUMNS ORDERS -> ORDER_ID.", response.getAnswer());
        assertFalse(response.getStructuredContext().contains("Unrelated column"));
        assertEquals("USER_TAB_COLUMNS ORDERS -> ORDER_ID.", response.getSources().get(0).getContent());
    }

    private String promptFor(LlmAnswerRequest request) throws Exception {
        LangChain4jAnswerClient client = new LangChain4jAnswerClient(
                new AnswerGenerationProperties(), new RuleBasedAnswerClient());
        Method method = LangChain4jAnswerClient.class.getDeclaredMethod("buildPrompt", LlmAnswerRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(client, request);
    }

    private AnswerGenerateTool tool(AgentAnswerAssembler assembler,
                                    LangChain4jAnswerClient model, boolean useModel) {
        AnswerGenerationProperties properties = new AnswerGenerationProperties();
        if (useModel) {
            properties.setMode("langchain4j");
            properties.getLangchain4j().setEnabled(true);
        }
        return new AnswerGenerateTool(assembler, new RuleBasedAnswerClient(), model, properties);
    }

    private LangChain4jAnswerClient modelReturning(String answer) {
        LangChain4jAnswerClient model = mock(LangChain4jAnswerClient.class);
        LlmAnswerResult generated = new LlmAnswerResult();
        generated.setAnswer(answer);
        generated.setProvider("MODEL");
        when(model.generate(any())).thenReturn(generated);
        return model;
    }

    private LlmAnswerRequest capturedRequest(LangChain4jAnswerClient model) {
        ArgumentCaptor<LlmAnswerRequest> captured = ArgumentCaptor.forClass(LlmAnswerRequest.class);
        verify(model).generate(captured.capture());
        return captured.getValue();
    }

    private AskResponse answer(String text, SourceItem... sources) {
        AskResponse response = new AskResponse();
        response.setAnswer(text);
        response.setSources(new ArrayList<>(List.of(sources)));
        return response;
    }

    private SourceItem source(String type, Long chunkId, Long documentId,
                              String content, String evidence) {
        SourceItem source = new SourceItem();
        source.setSourceType(type);
        source.setChunkId(chunkId);
        source.setDocumentId(documentId);
        source.setContent(content);
        source.setEvidenceSentences(List.of(evidence));
        return source;
    }

    private ChunkHit chunk(Long chunkId, Long documentId, String content) {
        ChunkHit hit = new ChunkHit();
        hit.setChunkId(chunkId);
        hit.setDocumentId(documentId);
        hit.setContent(content);
        return hit;
    }

    private RelationHit relation(Long chunkId, Long documentId, String evidence) {
        RelationHit hit = new RelationHit();
        hit.setChunkId(chunkId);
        hit.setDocumentId(documentId);
        hit.setSourceEntityName("TABLE_A");
        hit.setRelationType("RELATES_TO");
        hit.setTargetEntityName("TABLE_B");
        hit.setEvidenceText(evidence);
        return hit;
    }

    private RelationPathItem path(Long chunkId, Long documentId, String source,
                                  String target, String evidence) {
        RelationPathItem path = new RelationPathItem();
        path.setChunkId(chunkId);
        path.setDocumentId(documentId);
        path.setSourceEntity(source);
        path.setTargetEntity(target);
        path.setEvidenceText(evidence);
        path.setSummary(evidence);
        return path;
    }
}
