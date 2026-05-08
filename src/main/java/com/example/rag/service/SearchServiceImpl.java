package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.SearchMeta;
import com.example.rag.dto.SearchResult;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final DocumentChunkRepository documentChunkRepository;

    public SearchServiceImpl(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    @Override
    public SearchResult searchByKeyword(String keyword) {
        long start = System.currentTimeMillis();
        String cleanedKeyword = normalizeKeyword(keyword);

        List<ChunkHit> matchedChunks = new ArrayList<>();
        LinkedHashSet<Long> matchedDocumentIdSet = new LinkedHashSet<>();

        if (StringUtils.hasText(cleanedKeyword)) {
            List<DocumentChunk> chunks = documentChunkRepository.searchByKeyword(cleanedKeyword);
            for (DocumentChunk chunk : chunks) {
                ChunkHit hit = new ChunkHit();
                hit.setDocumentId(chunk.getDocumentId());
                hit.setChunkIndex(chunk.getChunkIndex());
                hit.setContent(chunk.getContent());
                hit.setTokenCount(chunk.getTokenCount());
                matchedChunks.add(hit);
                if (chunk.getDocumentId() != null) {
                    matchedDocumentIdSet.add(chunk.getDocumentId());
                }
            }
        }

        List<Long> matchedDocumentIds = new ArrayList<>(matchedDocumentIdSet);
        long costMs = System.currentTimeMillis() - start;

        SearchMeta searchMeta = new SearchMeta();
        searchMeta.setKeyword(cleanedKeyword);
        searchMeta.setTotalHits(matchedChunks.size());
        searchMeta.setCostMs(costMs);

        SearchResult result = new SearchResult();
        result.setKeyword(cleanedKeyword);
        result.setMatchedChunks(matchedChunks);
        result.setMatchedDocumentIds(matchedDocumentIds);
        result.setSearchMeta(searchMeta);
        return result;
    }

    @Override
    public SearchResult searchByQuestion(String question) {
        String keyword = extractKeyword(question);
        return searchByKeyword(keyword);
    }

    private String extractKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }

        String originalQuestion = question.trim();
        String cleaned = removeQuestionWords(originalQuestion);
        cleaned = removePunctuation(cleaned);
        cleaned = cleaned.trim();

        if (StringUtils.hasText(cleaned)) {
            return cleaned;
        }

        String fallback = removePunctuation(originalQuestion).trim();
        return StringUtils.hasText(fallback) ? fallback : originalQuestion;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return removePunctuation(keyword).trim();
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
}
