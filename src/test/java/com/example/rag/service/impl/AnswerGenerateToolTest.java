package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.SourceItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerGenerateToolTest {

    @Test
    void ruleModeKeepsStructuredSources() {
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        AgentToolInput input = chunkInput();
        AskResponse assembled = responseWithSource("rule answer");
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AnswerGenerateTool tool = new AnswerGenerateTool(
                assembler,
                new RuleBasedAnswerClient(),
                new LangChain4jAnswerClient(new AnswerGenerationProperties(), new RuleBasedAnswerClient()),
                new AnswerGenerationProperties()
        );

        AgentToolResult result = tool.execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertEquals("rule answer", response.getAnswer());
        assertEquals(1, response.getSources().size());
        assertEquals("CHUNK", response.getSources().get(0).getSourceType());
        assertEquals("RULE", response.getDebug().getAnswerProvider());
        assertFalse(response.getDebug().isAnswerFallbackUsed());
    }

    @Test
    void langChain4jFailureFallsBackAndKeepsSources() {
        AnswerGenerationProperties properties = new AnswerGenerationProperties();
        properties.setMode("langchain4j");
        properties.getLangchain4j().setEnabled(true);
        properties.getLangchain4j().setApiKey("");
        properties.getLangchain4j().setFallbackEnabled(true);

        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        AgentToolInput input = chunkInput();
        AskResponse assembled = responseWithSource("rule fallback answer");
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AnswerGenerateTool tool = new AnswerGenerateTool(
                assembler,
                new RuleBasedAnswerClient(),
                new LangChain4jAnswerClient(properties, new RuleBasedAnswerClient()),
                properties
        );

        AgentToolResult result = tool.execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertEquals("rule fallback answer", response.getAnswer());
        assertEquals(1, response.getSources().size());
        assertEquals("RULE", response.getDebug().getAnswerProvider());
        assertTrue(response.getDebug().isAnswerFallbackUsed());
        assertNotNull(response.getDebug().getAnswerErrorMessage());
    }

    private AgentToolInput chunkInput() {
        AgentToolInput input = new AgentToolInput();
        input.setQuestion("test question");
        List<ChunkHit> chunks = new ArrayList<>();
        ChunkHit chunk = new ChunkHit();
        chunk.setChunkId(1L);
        chunk.setDocumentId(2L);
        chunk.setContent("chunk evidence");
        chunks.add(chunk);
        input.setChunkHits(chunks);
        return input;
    }

    private AskResponse responseWithSource(String answer) {
        AskResponse response = new AskResponse();
        response.setQuestion("test question");
        response.setAnswer(answer);
        response.setDebug(new AskDebugInfo());
        List<SourceItem> sources = new ArrayList<>();
        SourceItem source = new SourceItem();
        source.setSourceType("CHUNK");
        source.setDocumentName("doc.md");
        source.setChunkId(1L);
        sources.add(source);
        response.setSources(sources);
        return response;
    }
}
