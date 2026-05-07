package com.example.rag.service;

import com.example.rag.dto.AskContext;
import com.example.rag.dto.AskResponse;
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
import java.util.Locale;

@Service
public class AskServiceImpl implements AskService {

    private static final Logger log = LoggerFactory.getLogger(AskServiceImpl.class);

    private final SearchService searchService;
    private final ContextBuilderService contextBuilderService;
    private final QueryLogRepository queryLogRepository;

    public AskServiceImpl(SearchService searchService,
                          ContextBuilderService contextBuilderService,
                          QueryLogRepository queryLogRepository) {
        this.searchService = searchService;
        this.contextBuilderService = contextBuilderService;
        this.queryLogRepository = queryLogRepository;
    }

    @Override
    public AskResponse ask(String question) {
        long start = System.currentTimeMillis();

        String keyword = extractKeyword(question);
        log.info("original question = {}", question);
        log.info("cleaned keyword = {}", keyword);

        SearchResult searchResult = searchService.searchByKeyword(keyword);
        AskContext askContext = contextBuilderService.buildContext(question, keyword, searchResult);

        List<ChunkResponse> chunks = new ArrayList<>(askContext.getTopChunks());
        log.info("chunks size = {}", chunks.size());

        String answer = buildAnswer(chunks);

        AskResponse response = new AskResponse();
        response.setQuestion(question);
        response.setKeyword(keyword);
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

        String cleaned = originalQuestion;
        cleaned = removeQuestionWords(cleaned);
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
        result = result.replace("什么是", "");
        result = result.replace("是什么", "");
        result = result.replace("是啥", "");
        result = result.replace("介绍一下", "");
        result = result.replace("请说明", "");
        result = result.replace("请问", "");

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
        result = result.replace("？", " ");
        result = result.replace(".", " ");
        result = result.replace("。", " ");
        result = result.replace(",", " ");
        result = result.replace("，", " ");
        result = result.replace("!", " ");
        result = result.replace("！", " ");
        result = result.replace(":", " ");
        result = result.replace("：", " ");
        result = result.replace(";", " ");
        result = result.replace("；", " ");
        result = result.replace("、", " ");
        result = result.replace("“", " ");
        result = result.replace("”", " ");
        result = result.replace("\"", " ");
        result = result.replace("'", " ");
        result = result.replace("（", " ");
        result = result.replace("）", " ");
        result = result.replace("(", " ");
        result = result.replace(")", " ");
        result = result.replace("[", " ");
        result = result.replace("]", " ");
        result = result.replace("【", " ");
        result = result.replace("】", " ");
        result = result.replaceAll("\\s+", " ");
        return result;
    }

    private String buildAnswer(List<ChunkResponse> chunks) {
        if (chunks.isEmpty()) {
            return "No relevant knowledge found.";
        }

        StringBuilder summary = new StringBuilder("根据知识库内容，");
        int limit = Math.min(2, chunks.size());
        for (int i = 0; i < limit; i++) {
            ChunkResponse chunk = chunks.get(i);
            String content = chunk.getHighlightContent();
            if (!StringUtils.hasText(content)) {
                content = chunk.getContent();
            }
            if (!StringUtils.hasText(content)) {
                continue;
            }

            String snippet = content.length() > 80 ? content.substring(0, 80) : content;
            if (i > 0) {
                summary.append("；");
            }
            summary.append(snippet);
        }
        summary.append("。");
        return summary.toString();
    }
}
