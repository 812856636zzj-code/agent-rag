package com.example.rag.service;

import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class DocumentChunkService {

    private static final int CHUNK_SIZE = 500;
    private static final List<String> NOISE_KEYWORDS = Arrays.asList(
            "classpath",
            "org.springframework",
            "hibernate",
            "hhh000",
            "jtaplatform",
            "java.exe",
            "log4j",
            "jackson",
            "tomcat",
            "dialect",
            "info",
            "warn",
            "error"
    );

    private final DocumentChunkRepository documentChunkRepository;

    public DocumentChunkService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    public int saveChunks(Long documentId, String text) {
        List<DocumentChunk> chunks = buildChunks(documentId, text);
        if (!chunks.isEmpty()) {
            documentChunkRepository.saveAll(chunks);
        }
        return chunks.size();
    }

    public List<DocumentChunk> getChunksByDocumentId(Long documentId) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        return filterNoiseChunks(chunks);
    }

    public List<Map<String, Object>> getPendingChunks() {
        List<DocumentChunk> chunks = filterNoiseChunks(
                documentChunkRepository.findByEmbeddingStatusOrderByDocumentIdDescChunkIndexAsc("PENDING")
        );
        List<Map<String, Object>> results = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", chunk.getId());
            item.put("documentId", chunk.getDocumentId());
            item.put("chunkIndex", chunk.getChunkIndex());
            item.put("content", chunk.getContent());
            item.put("tokenCount", chunk.getTokenCount());
            results.add(item);
        }
        return results;
    }

    @Transactional
    public int markEmbedded(Long documentId) {
        return documentChunkRepository.updateEmbeddingStatusByDocumentId(documentId, "DONE");
    }

    @Transactional
    public int rebuildChunks(Long documentId, String text) {
        documentChunkRepository.deleteByDocumentId(documentId);
        return saveChunks(documentId, text);
    }

    public String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String[] lines = text.split("\\R");
        List<String> cleanedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.matches("^[\\p{Punct}\\s]+$")) {
                continue;
            }
            if (containsNoiseKeyword(trimmed)) {
                continue;
            }
            cleanedLines.add(trimmed);
        }
        return String.join(System.lineSeparator(), cleanedLines).trim();
    }

    private List<DocumentChunk> buildChunks(Long documentId, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        int chunkIndex = 0;
        for (int start = 0; start < text.length(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            String content = text.substring(start, end);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setContent(content);
            chunk.setTokenCount(content.length());
            chunk.setEmbeddingStatus("PENDING");
            chunks.add(chunk);

            chunkIndex++;
        }
        return chunks;
    }

    private List<DocumentChunk> filterNoiseChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocumentChunk> filtered = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk != null && !containsNoiseKeyword(chunk.getContent())) {
                filtered.add(chunk);
            }
        }
        return filtered;
    }

    private boolean containsNoiseKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : NOISE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
