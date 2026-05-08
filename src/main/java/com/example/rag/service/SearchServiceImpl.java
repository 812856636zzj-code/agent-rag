package com.example.rag.service;

import com.example.rag.dto.ChunkHit;
import com.example.rag.dto.ChunkResponse;
import com.example.rag.dto.SearchMeta;
import com.example.rag.dto.SearchResult;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class SearchServiceImpl implements SearchService {

    private final DocumentChunkRepository documentChunkRepository;

    public SearchServiceImpl(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    @Override
    public SearchResult searchByKeyword(String keyword) {
        List<ChunkResponse> chunkResponses = searchChunkResponses(keyword, Integer.MAX_VALUE);
        List<ChunkHit> matchedChunks = new ArrayList<>();
        LinkedHashSet<Long> matchedDocumentIdSet = new LinkedHashSet<>();

        for (ChunkResponse chunk : chunkResponses) {
            ChunkHit hit = new ChunkHit();
            hit.setDocumentId(chunk.getDocumentId());
            hit.setChunkIndex(chunk.getChunkIndex());
            hit.setContent(chunk.getContent());
            hit.setHighlightContent(chunk.getHighlightContent());
            matchedChunks.add(hit);
            if (chunk.getDocumentId() != null) {
                matchedDocumentIdSet.add(chunk.getDocumentId());
            }
        }

        List<Long> matchedDocumentIds = new ArrayList<>(matchedDocumentIdSet);

        SearchMeta searchMeta = new SearchMeta();
        searchMeta.setTotalHits(matchedChunks.size());
        searchMeta.setMatchedDocumentIds(matchedDocumentIds);

        SearchResult result = new SearchResult();
        result.setKeyword(keyword);
        result.setMatchedChunks(matchedChunks);
        result.setMatchedDocumentIds(matchedDocumentIds);
        result.setSearchMeta(searchMeta);
        return result;
    }

    @Override
    public List<ChunkResponse> searchChunkResponses(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }

        List<DocumentChunk> chunks = documentChunkRepository.searchByKeyword(keyword);
        List<ChunkResponse> results = new ArrayList<>();

        int max = Math.min(limit, chunks.size());
        for (int i = 0; i < max; i++) {
            DocumentChunk chunk = chunks.get(i);
            ChunkResponse item = new ChunkResponse();
            item.setDocumentId(chunk.getDocumentId());
            item.setChunkIndex(chunk.getChunkIndex());
            item.setContent(chunk.getContent());
            item.setHighlightContent(buildHighlightContent(chunk.getContent(), keyword));
            results.add(item);
        }

        return results;
    }

    private String buildHighlightContent(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String trimmed = content.trim();
        if (!StringUtils.hasText(keyword)) {
            return buildSummary(trimmed, 120);
        }

        int index = trimmed.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return buildSummary(trimmed, 120);
        }

        int start = Math.max(0, index - 35);
        int end = Math.min(trimmed.length(), index + keyword.length() + 35);

        String prefix = start > 0 ? "..." : "";
        String suffix = end < trimmed.length() ? "..." : "";
        String matched = trimmed.substring(index, index + keyword.length());
        String snippet = trimmed.substring(start, index)
                + "[[" + matched + "]]"
                + trimmed.substring(index + keyword.length(), end);
        return prefix + snippet + suffix;
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
