package com.example.rag.service;

import com.example.rag.dto.EntityHit;
import com.example.rag.dto.RelationBuildResult;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentChunkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkService.class);
    private static final int CHUNK_SIZE = 500;
    private static final int MARKDOWN_CHUNK_SIZE = 900;
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern SOURCE_HEADING_PATTERN = Pattern.compile("^##\\s+(Page|Slide)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
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
    private final EntityExtractionService entityExtractionService;
    private final EntityPersistenceService entityPersistenceService;
    private final RelationBuilderService relationBuilderService;

    public DocumentChunkService(DocumentChunkRepository documentChunkRepository,
                                EntityExtractionService entityExtractionService,
                                EntityPersistenceService entityPersistenceService,
                                RelationBuilderService relationBuilderService) {
        this.documentChunkRepository = documentChunkRepository;
        this.entityExtractionService = entityExtractionService;
        this.entityPersistenceService = entityPersistenceService;
        this.relationBuilderService = relationBuilderService;
    }

    @Transactional
    public int saveChunks(Long documentId, String text) {
        List<DocumentChunk> chunks = buildChunks(documentId, text);
        if (!chunks.isEmpty()) {
            List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
            log.info("chunks saved, documentId = {}, chunkCount = {}", documentId, savedChunks.size());
            extractAndPersistEntities(documentId, savedChunks);
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
        entityPersistenceService.deleteLinksByDocumentId(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
        return saveChunks(documentId, text);
    }

    private void extractAndPersistEntities(Long documentId, List<DocumentChunk> savedChunks) {
        for (DocumentChunk chunk : savedChunks) {
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }

            try {
                List<EntityHit> entities = entityExtractionService.extractEntities(chunk.getContent());
                log.info("entity extraction result, documentId = {}, chunkCount = {}, chunkId = {}, chunkIndex = {}, entityCount = {}",
                        documentId, savedChunks.size(), chunk.getId(), chunk.getChunkIndex(), entities.size());
                entityPersistenceService.saveChunkEntities(
                        documentId,
                        chunk.getId(),
                        chunk.getContent(),
                        entities
                );
                try {
                    RelationBuildResult relationBuildResult = relationBuilderService.buildRelationsForChunk(
                            documentId,
                            chunk.getId(),
                            chunk.getContent()
                    );
                    int relationCount = relationBuildResult == null || relationBuildResult.getRelationCount() == null
                            ? 0
                            : relationBuildResult.getRelationCount();
                    log.info("relation build result, documentId = {}, chunkId = {}, chunkIndex = {}, relationCount = {}",
                            documentId, chunk.getId(), chunk.getChunkIndex(), relationCount);
                } catch (RuntimeException relationEx) {
                    log.error("relation build failed, documentId = {}, chunkId = {}, chunkIndex = {}",
                            documentId, chunk.getId(), chunk.getChunkIndex(), relationEx);
                }
            } catch (RuntimeException ex) {
                log.error("entity extraction failed, documentId = {}, chunkId = {}",
                        documentId, chunk.getId(), ex);
            }
        }
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

        List<MarkdownBlock> blocks = parseMarkdownBlocks(text);
        if (!blocks.isEmpty()) {
            return buildMarkdownChunks(documentId, blocks);
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

    private List<DocumentChunk> buildMarkdownChunks(Long documentId, List<MarkdownBlock> blocks) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String currentSource = "";
        String currentSectionPath = "";
        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;

        for (MarkdownBlock block : blocks) {
            if (block == null || !StringUtils.hasText(block.getText())) {
                continue;
            }
            currentSource = StringUtils.hasText(block.getSource()) ? block.getSource() : currentSource;
            currentSectionPath = StringUtils.hasText(block.getSectionPath()) ? block.getSectionPath() : currentSectionPath;

            String decorated = decorateMarkdownBlock(block);
            if (current.length() > 0 && current.length() + decorated.length() > MARKDOWN_CHUNK_SIZE) {
                chunks.add(newChunk(documentId, chunkIndex++, current.toString().trim()));
                current.setLength(0);
            }
            if (current.length() == 0) {
                appendChunkHeader(current, currentSource, currentSectionPath);
            }
            current.append(decorated).append("\n\n");
        }

        if (current.length() > 0) {
            chunks.add(newChunk(documentId, chunkIndex, current.toString().trim()));
        }
        return chunks;
    }

    private DocumentChunk newChunk(Long documentId, int chunkIndex, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setTokenCount(content.length());
        chunk.setEmbeddingStatus("PENDING");
        return chunk;
    }

    private void appendChunkHeader(StringBuilder current, String source, String sectionPath) {
        current.append("<!-- source: ").append(StringUtils.hasText(source) ? source : "document").append(" -->\n");
        current.append("<!-- section: ").append(StringUtils.hasText(sectionPath) ? sectionPath : "root").append(" -->\n\n");
    }

    private String decorateMarkdownBlock(MarkdownBlock block) {
        String text = block.getText();
        if (block.isTable()) {
            return text;
        }
        return text.trim();
    }

    private List<MarkdownBlock> parseMarkdownBlocks(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        String[] lines = markdown.replace("\r", "\n").split("\n");
        String[] headings = new String[6];
        String currentSource = "";
        StringBuilder paragraph = new StringBuilder();
        StringBuilder table = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                flushParagraph(blocks, paragraph, headings, currentSource);
                flushTable(blocks, table, headings, currentSource);
                continue;
            }

            Matcher headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                flushParagraph(blocks, paragraph, headings, currentSource);
                flushTable(blocks, table, headings, currentSource);

                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();
                headings[level - 1] = headingText;
                for (int i = level; i < headings.length; i++) {
                    headings[i] = null;
                }
                Matcher sourceMatcher = SOURCE_HEADING_PATTERN.matcher(line);
                if (sourceMatcher.matches()) {
                    currentSource = sourceMatcher.group(1) + " " + sourceMatcher.group(2);
                }
                blocks.add(new MarkdownBlock(line, false, buildSectionPath(headings), currentSource));
                continue;
            }

            if (line.startsWith("|")) {
                flushParagraph(blocks, paragraph, headings, currentSource);
                table.append(line).append("\n");
                continue;
            }

            flushTable(blocks, table, headings, currentSource);
            if (paragraph.length() > 0) {
                paragraph.append("\n");
            }
            paragraph.append(line);
        }

        flushParagraph(blocks, paragraph, headings, currentSource);
        flushTable(blocks, table, headings, currentSource);
        return blocks;
    }

    private void flushParagraph(List<MarkdownBlock> blocks, StringBuilder paragraph, String[] headings, String source) {
        if (!StringUtils.hasText(paragraph.toString())) {
            paragraph.setLength(0);
            return;
        }
        blocks.add(new MarkdownBlock(paragraph.toString().trim(), false, buildSectionPath(headings), source));
        paragraph.setLength(0);
    }

    private void flushTable(List<MarkdownBlock> blocks, StringBuilder table, String[] headings, String source) {
        if (!StringUtils.hasText(table.toString())) {
            table.setLength(0);
            return;
        }
        blocks.add(new MarkdownBlock(table.toString().trim(), true, buildSectionPath(headings), source));
        table.setLength(0);
    }

    private String buildSectionPath(String[] headings) {
        List<String> values = new ArrayList<>();
        for (String heading : headings) {
            if (StringUtils.hasText(heading)) {
                values.add(heading);
            }
        }
        return String.join(" > ", values);
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

    private static class MarkdownBlock {

        private final String text;
        private final boolean table;
        private final String sectionPath;
        private final String source;

        private MarkdownBlock(String text, boolean table, String sectionPath, String source) {
            this.text = text;
            this.table = table;
            this.sectionPath = sectionPath;
            this.source = source;
        }

        private String getText() {
            return text;
        }

        private boolean isTable() {
            return table;
        }

        private String getSectionPath() {
            return sectionPath;
        }

        private String getSource() {
            return source;
        }
    }

}
