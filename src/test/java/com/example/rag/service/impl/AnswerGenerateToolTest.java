package com.example.rag.service.impl;

import com.example.rag.config.AnswerGenerationProperties;
import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.AskContext;
import com.example.rag.dto.AskDebugInfo;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.LlmAnswerResult;
import com.example.rag.dto.LlmAnswerRequest;
import com.example.rag.dto.SourceItem;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.service.AnswerBuilderService;
import com.example.rag.service.ContextBuilderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
        assertEquals("chunk evidence", response.getAnswer());
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
        assertEquals("chunk evidence", response.getAnswer());
        assertEquals(1, response.getSources().size());
        assertEquals("RULE", response.getDebug().getAnswerProvider());
        assertTrue(response.getDebug().isAnswerFallbackUsed());
        assertNotNull(response.getDebug().getAnswerErrorMessage());
    }

    @Test
    void abstainsWhenChunksHaveNoSupportingEvidence() {
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        AgentToolInput input = chunkInput();
        AskResponse assembled = responseWithSource("unsupported affirmative answer");
        assembled.getSources().get(0).setEvidenceSentences(new ArrayList<>());
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
        assertTrue(response.getAnswer().contains("\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599"));
        assertTrue(response.getSources().isEmpty(), "Unverified sources must not be presented as citations");
        assertEquals(1, response.getMergedChunkCount());
        assertEquals(1, response.getDebug().getHitCount());
        assertEquals("EVIDENCE_GUARD", result.getAnswerProvider());
    }

    @Test
    void doesNotCallModelWhenChunksHaveNoSupportingEvidence() {
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        AgentToolInput input = chunkInput();
        AskResponse assembled = responseWithSource("unsupported affirmative answer");
        assembled.getSources().get(0).setEvidenceSentences(new ArrayList<>());
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AnswerGenerationProperties properties = new AnswerGenerationProperties();
        properties.setMode("langchain4j");
        properties.getLangchain4j().setEnabled(true);
        LangChain4jAnswerClient model = mock(LangChain4jAnswerClient.class);
        LlmAnswerResult generated = new LlmAnswerResult();
        generated.setAnswer("unsupported model answer");
        generated.setProvider("MODEL");
        when(model.generate(any())).thenReturn(generated);

        AnswerGenerateTool tool = new AnswerGenerateTool(
                assembler, new RuleBasedAnswerClient(), model, properties
        );

        AgentToolResult result = tool.execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertTrue(response.getAnswer().contains("\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599"));
        verifyNoInteractions(model);
    }

    @Test
    void retrievedChunkForAnotherTableDoesNotBecomeAnAnswer() {
        String question = "ORDERS\u8868\u6709\u54ea\u4e9b\u5b57\u6bb5\uff1f";
        ChunkHit hit = new ChunkHit();
        hit.setContent("USERS\u8868\u5b57\u6bb5\u5305\u62ecUSER_ID\u3001NAME\u3001AGE\u3002");
        AgentToolInput input = new AgentToolInput();
        input.setQuestion(question);
        input.setChunkHits(List.of(hit));

        AskContext context = new AskContext();
        context.setTopChunks(List.of(hit));
        context.setStructuredContext(hit.getContent());
        ContextBuilderService contextBuilder = mock(ContextBuilderService.class);
        when(contextBuilder.buildContext(question, "", input.getChunkHits())).thenReturn(context);
        AnswerBuilderService answerBuilder = mock(AnswerBuilderService.class);
        when(answerBuilder.buildAnswer(context)).thenReturn("USERS has USER_ID, NAME, and AGE fields");
        AgentAnswerAssembler assembler = new AgentAnswerAssembler(
                contextBuilder,
                answerBuilder,
                new EvidenceSelectorImpl(new SentenceSplitterImpl()),
                mock(DocumentChunkRepository.class),
                mock(DocumentRepository.class)
        );
        AnswerGenerateTool tool = new AnswerGenerateTool(
                assembler,
                new RuleBasedAnswerClient(),
                new LangChain4jAnswerClient(new AnswerGenerationProperties(), new RuleBasedAnswerClient()),
                new AnswerGenerationProperties()
        );

        AgentToolResult result = tool.execute(input);
        AskResponse response = (AskResponse) result.getData();

        assertTrue(result.isSuccess());
        assertTrue(response.getAnswer().contains("\u672a\u68c0\u7d22\u5230\u8db3\u591f\u76f8\u5173\u7684\u8d44\u6599"));
        assertTrue(response.getSources().isEmpty());
        assertEquals(1, response.getMergedChunkCount());
        assertEquals(1, response.getDebug().getHitCount());
    }

    @Test
    void removesUnsupportedCitationFromMixedEvidenceBeforeModelGeneration() {
        AgentAnswerAssembler assembler = mock(AgentAnswerAssembler.class);
        AgentToolInput input = chunkInput();
        ChunkHit secondHit = new ChunkHit();
        secondHit.setChunkId(3L);
        secondHit.setDocumentId(4L);
        secondHit.setContent("unrelated raw content");
        input.getChunkHits().add(secondHit);

        AskResponse assembled = responseWithSource("supported answer");
        assembled.setMergedChunkCount(2);
        assembled.getDebug().setHitCount(2);
        SourceItem unsupported = new SourceItem();
        unsupported.setSourceType("CHUNK");
        unsupported.setDocumentName("unrelated.md");
        unsupported.setChunkId(3L);
        unsupported.setEvidenceSentences(List.of(" ", ""));
        assembled.getSources().add(unsupported);
        when(assembler.buildChunkResponse(input.getQuestion(), input.getChunkHits())).thenReturn(assembled);

        AnswerGenerationProperties properties = new AnswerGenerationProperties();
        properties.setMode("langchain4j");
        properties.getLangchain4j().setEnabled(true);
        LangChain4jAnswerClient model = mock(LangChain4jAnswerClient.class);
        LlmAnswerResult generated = new LlmAnswerResult();
        generated.setAnswer("supported answer");
        generated.setProvider("MODEL");
        when(model.generate(any())).thenReturn(generated);
        AnswerGenerateTool tool = new AnswerGenerateTool(
                assembler, new RuleBasedAnswerClient(), model, properties
        );

        AgentToolResult result = tool.execute(input);
        AskResponse response = (AskResponse) result.getData();
        ArgumentCaptor<LlmAnswerRequest> request = ArgumentCaptor.forClass(LlmAnswerRequest.class);
        verify(model).generate(request.capture());

        assertTrue(result.isSuccess());
        assertEquals(1, response.getSources().size(), "Only evidenced sources should be published");
        assertEquals("doc.md", response.getSources().get(0).getDocumentName());
        assertEquals(1, request.getValue().getSources().size(), "Model source preview must match citations");
        assertEquals(2, response.getMergedChunkCount(), "Retrieval diagnostics still count both hits");
        assertEquals(2, response.getDebug().getHitCount());
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
        response.setMergedChunkCount(1);
        AskDebugInfo debug = new AskDebugInfo();
        debug.setHitCount(1);
        response.setDebug(debug);
        List<SourceItem> sources = new ArrayList<>();
        SourceItem source = new SourceItem();
        source.setSourceType("CHUNK");
        source.setDocumentName("doc.md");
        source.setChunkId(1L);
        source.setEvidenceSentences(List.of("chunk evidence"));
        sources.add(source);
        response.setSources(sources);
        return response;
    }
}
