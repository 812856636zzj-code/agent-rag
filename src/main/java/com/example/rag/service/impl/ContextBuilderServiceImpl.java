package com.example.rag.service.impl;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.SearchResult;
import com.example.rag.service.ContextBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ContextBuilderServiceImpl implements ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderServiceImpl.class);
    private static final int TOP_CHUNK_LIMIT = 5;
    private static final int HINT_MAX_LENGTH = 120;

    @Override
    public AskContext buildContext(String question, String keyword, List<ChunkHit> matchedChunks) {
        log.info("buildContext start, keyword = {}", keyword);

        AskContext context = new AskContext();
        context.setQuestion(question);
        context.setKeyword(keyword);

        List<ChunkHit> safeMatchedChunks = matchedChunks == null ? new ArrayList<>() : new ArrayList<>(matchedChunks);
        context.setMatchedChunks(safeMatchedChunks);
        log.info("buildContext matchedChunks size = {}", safeMatchedChunks.size());

        if (safeMatchedChunks.isEmpty()) {
            context.setTopChunks(new ArrayList<>());
            context.setSourceDocIds(new ArrayList<>());
            context.setAnswerHints(new ArrayList<>());
            context.setStructuredContext(buildEmptyStructuredContext(question, keyword));
            log.info("buildContext structuredContext generated = {}", context.getStructuredContext() != null && !context.getStructuredContext().isEmpty());
            return context;
        }

        List<ChunkHit> topChunks = buildTopChunks(safeMatchedChunks);
        List<Long> sourceDocIds = buildSourceDocIds(topChunks);
        List<String> answerHints = buildAnswerHints(topChunks);

        context.setTopChunks(topChunks);
        context.setSourceDocIds(sourceDocIds);
        context.setAnswerHints(answerHints);
        context.setStructuredContext(buildStructuredContext(question, keyword, safeMatchedChunks.size(), answerHints));
        log.info("buildContext structuredContext generated = {}", context.getStructuredContext() != null && !context.getStructuredContext().isEmpty());
        return context;
    }

    @Override
    public AskContext buildContext(String question, String keyword, SearchResult searchResult) {
        List<ChunkHit> matchedChunks = searchResult == null ? new ArrayList<>() : searchResult.getMatchedChunks();
        return buildContext(question, keyword, matchedChunks);
    }

    private List<ChunkHit> buildTopChunks(List<ChunkHit> matchedChunks) {
        int max = Math.min(TOP_CHUNK_LIMIT, matchedChunks.size());
        List<ChunkHit> topChunks = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            topChunks.add(matchedChunks.get(i));
        }
        return topChunks;
    }

    private List<Long> buildSourceDocIds(List<ChunkHit> topChunks) {
        LinkedHashSet<Long> sourceDocIds = new LinkedHashSet<>();
        for (ChunkHit chunk : topChunks) {
            if (chunk != null && chunk.getDocumentId() != null) {
                sourceDocIds.add(chunk.getDocumentId());
            }
        }
        return new ArrayList<>(sourceDocIds);
    }

    private List<String> buildAnswerHints(List<ChunkHit> topChunks) {
        List<String> answerHints = new ArrayList<>();
        for (ChunkHit chunk : topChunks) {
            if (chunk == null) {
                continue;
            }
            String evidence = extractEvidence(chunk);
            if (evidence != null && !evidence.isEmpty()) {
                answerHints.add(evidence);
            }
        }
        return answerHints;
    }

    private String extractEvidence(ChunkHit chunk) {
        String content = chunk.getContent();
        if (content == null) {
            return null;
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > HINT_MAX_LENGTH) {
            return normalized.substring(0, HINT_MAX_LENGTH) + "...";
        }
        return normalized;
    }

    private String buildEmptyStructuredContext(String question, String keyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u95ee\u9898\uff1a").append(valueOrEmpty(question)).append("\n");
        sb.append("\u5173\u952e\u8bcd\uff1a").append(valueOrEmpty(keyword)).append("\n");
        sb.append("\u547d\u4e2d\u5757\u6570\uff1a0").append("\n");
        sb.append("\u4e3b\u8981\u8bc1\u636e\uff1a").append("\n");
        sb.append("\u6682\u672a\u68c0\u7d22\u5230\u53ef\u7528\u8bc1\u636e\u3002");
        return sb.toString();
    }

    private String buildStructuredContext(String question, String keyword, int totalHits, List<String> answerHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u95ee\u9898\uff1a").append(valueOrEmpty(question)).append("\n");
        sb.append("\u5173\u952e\u8bcd\uff1a").append(valueOrEmpty(keyword)).append("\n");
        sb.append("\u547d\u4e2d\u5757\u6570\uff1a").append(totalHits).append("\n");
        sb.append("\u4e3b\u8981\u8bc1\u636e\uff1a");

        if (answerHints.isEmpty()) {
            sb.append("\n").append("\u6682\u672a\u68c0\u7d22\u5230\u53ef\u7528\u8bc1\u636e\u3002");
            return sb.toString();
        }

        for (int i = 0; i < answerHints.size(); i++) {
            sb.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(answerHints.get(i));
        }
        return sb.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
