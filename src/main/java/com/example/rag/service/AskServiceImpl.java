package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.ChunkResponse;
import com.example.rag.dto.SearchResult;
import com.example.rag.entity.QueryLog;
import com.example.rag.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AskServiceImpl implements AskService {

    private static final Logger log = LoggerFactory.getLogger(AskServiceImpl.class);

    private final SearchService searchService;
    private final ContextBuilderService contextBuilderService;
    private final AnswerBuilderService answerBuilderService;
    private final QueryLogRepository queryLogRepository;

    public AskServiceImpl(SearchService searchService,
                          ContextBuilderService contextBuilderService,
                          AnswerBuilderService answerBuilderService,
                          QueryLogRepository queryLogRepository) {
        this.searchService = searchService;
        this.contextBuilderService = contextBuilderService;
        this.answerBuilderService = answerBuilderService;
        this.queryLogRepository = queryLogRepository;
    }

    @Override
    public AskResponse ask(String question) {
        long start = System.currentTimeMillis();

        String keyword = extractKeyword(question);
        log.info("question = {}", question);
        log.info("keyword = {}", keyword);

        SearchResult searchResult = searchService.searchByQuestion(question);
        int matchedChunkCount = searchResult.getMatchedChunks() == null ? 0 : searchResult.getMatchedChunks().size();
        log.info("matchedChunks size = {}", matchedChunkCount);
        String finalKeyword = StringUtils.hasText(searchResult.getKeyword()) ? searchResult.getKeyword() : keyword;
        AskContext askContext = contextBuilderService.buildContext(question, finalKeyword, searchResult.getMatchedChunks());
        int topChunkCount = askContext.getTopChunks() == null ? 0 : askContext.getTopChunks().size();
        log.info("context top chunks size = {}", topChunkCount);

        List<ChunkResponse> chunks = toChunkResponses(askContext.getTopChunks(), finalKeyword);
        log.info("chunks size = {}", chunks.size());

        String answer = answerBuilderService.buildAnswer(askContext);

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setKeyword(finalKeyword);
        response.setChunks(chunks);
        response.setStructuredContext(askContext.getStructuredContext());
        response.setAnswer(answer);

        Long sourceDocId = chunks.isEmpty() ? null : chunks.get(0).getDocumentId();
        long responseTimeMs = System.currentTimeMillis() - start;
        saveQueryLog(question, answer, sourceDocId, responseTimeMs);

        return response;
    }

    @Override
    public void saveQueryLog(String question, String answer, Long sourceDocId, long responseTimeMs) {
        QueryLog queryLog = new QueryLog();
        queryLog.setQuestion(question);
        queryLog.setAnswer(answer);
        queryLog.setSourceDocId(sourceDocId);
        queryLog.setResponseTimeMs(responseTimeMs);
        queryLog.setCreateTime(LocalDateTime.now());
        queryLogRepository.save(queryLog);
    }

    private String extractKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }

        String originalQuestion = question.trim();

        if (originalQuestion.contains("\u6392\u5e8f")) {
            return "\u6392\u5e8f";
        }
        if (originalQuestion.contains("\u68c0\u7d22")) {
            return "\u68c0\u7d22";
        }
        if (originalQuestion.contains("/search")) {
            return "/search";
        }

        String cleaned = removeQuestionWords(originalQuestion);
        cleaned = removePunctuation(cleaned);
        cleaned = cleaned.trim();

        if (StringUtils.hasText(cleaned)) {
            return cleaned;
        }

        String fallback = removePunctuation(originalQuestion).trim();
        return StringUtils.hasText(fallback) ? fallback : originalQuestion;
    }

    private String removeQuestionWords(String text) {
        String result = text;
        result = result.replace("\u4ec0\u4e48\u662f", "");
        result = result.replace("\u662f\u4ec0\u4e48", "");
        result = result.replace("\u662f\u5565", "");
        result = result.replace("\u4ecb\u7ecd\u4e00\u4e0b", "");
        result = result.replace("\u8bf7\u8bf4\u660e", "");
        result = result.replace("\u8bf7\u4ecb\u7ecd", "");
        result = result.replace("\u8bf7\u95ee", "");
        result = result.replaceAll("(?i)\\bhow\\b", "");
        result = result.replaceAll("(?i)\\bwhat\\b", "");
        result = result.replaceAll("(?i)\\bwhy\\b", "");
        result = result.replaceAll("(?i)\\bwho\\b", "");
        result = result.replaceAll("(?i)\\bwhen\\b", "");
        result = result.replaceAll("(?i)\\bwhere\\b", "");
        return result;
    }

    private String removePunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String result = text;
        result = result.replace("?", " ");
        result = result.replace("\uff1f", " ");
        result = result.replace(".", " ");
        result = result.replace("\u3002", " ");
        result = result.replace(",", " ");
        result = result.replace("\uff0c", " ");
        result = result.replace("!", " ");
        result = result.replace("\uff01", " ");
        result = result.replace(":", " ");
        result = result.replace("\uff1a", " ");
        result = result.replace(";", " ");
        result = result.replace("\uff1b", " ");
        result = result.replace("\u3001", " ");
        result = result.replace("\u201c", " ");
        result = result.replace("\u201d", " ");
        result = result.replace("\"", " ");
        result = result.replace("'", " ");
        result = result.replace("\uff08", " ");
        result = result.replace("\uff09", " ");
        result = result.replace("(", " ");
        result = result.replace(")", " ");
        result = result.replace("[", " ");
        result = result.replace("]", " ");
        result = result.replace("\u3010", " ");
        result = result.replace("\u3011", " ");
        result = result.replaceAll("\\s+", " ");
        return result;
    }

    private List<ChunkResponse> toChunkResponses(List<ChunkHit> hits, String keyword) {
        List<ChunkResponse> chunks = new ArrayList<>();
        if (hits == null || hits.isEmpty()) {
            return chunks;
        }

        for (ChunkHit hit : hits) {
            ChunkResponse chunk = new ChunkResponse();
            chunk.setDocumentId(hit.getDocumentId());
            chunk.setChunkIndex(hit.getChunkIndex());
            chunk.setContent(hit.getContent());
            chunk.setHighlightContent(buildSnippet(hit.getContent(), keyword));
            chunks.add(chunk);
        }
        return chunks;
    }

    private String buildSnippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String trimmed = content.trim();
        if (!StringUtils.hasText(keyword)) {
            return buildSummary(trimmed, 120);
        }

        int index = trimmed.indexOf(keyword);
        if (index < 0) {
            return buildSummary(trimmed, 120);
        }

        int start = Math.max(0, index - 35);
        int end = Math.min(trimmed.length(), index + keyword.length() + 35);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < trimmed.length() ? "..." : "";
        return prefix
                + trimmed.substring(start, index)
                + "[[" + trimmed.substring(index, index + keyword.length()) + "]]"
                + trimmed.substring(index + keyword.length(), end)
                + suffix;
    }

    private String buildSummary(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
