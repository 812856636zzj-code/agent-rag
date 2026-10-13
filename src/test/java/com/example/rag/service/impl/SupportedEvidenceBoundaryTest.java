package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.dto.SourceItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportedEvidenceBoundaryTest {

    @Test
    void modelRequestExcludesUnsupportedChunksAndStructuredContext() {
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
}
