package com.example.rag.service.impl;

import com.example.rag.dto.EntityHit;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.enums.EntityType;
import com.example.rag.service.EntityExtractionService;
import com.example.rag.service.EntityPersistenceService;
import com.example.rag.service.EntityStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractionServiceImpl implements EntityExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractionServiceImpl.class);

    private static final List<String> LIBRARY_VALUES = Arrays.asList(
            "PDFBox",
            "Jackson",
            "Lombok",
            "Spring Boot",
            "Oracle",
            "MyBatis",
            "Hibernate",
            "JPA",
            "Tomcat"
    );

    private static final List<String> STATUS_VALUES = Arrays.asList(
            "SUCCESS",
            "FAILED",
            "PENDING",
            "UPLOADED",
            "EMBEDDING_DONE",
            "ERROR",
            "DONE"
    );

    private static final List<String> CONFIG_FILE_VALUES = Arrays.asList(
            "application.yml",
            "application.properties"
    );

    private static final Pattern API_PATTERN = Pattern.compile("/[A-Za-z0-9_\\-/{}/.]+");
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\bRAG_[A-Z0-9_]+\\b");
    private static final Pattern UPPER_FIELD_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}\\b");
    private static final Pattern CAMEL_FIELD_PATTERN = Pattern.compile("\\b[a-z]+[A-Z][A-Za-z0-9]*\\b");
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("\\b[a-z]+(?:\\.[a-zA-Z0-9_-]+)+\\b");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*Service\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(?:Context|Hit|Result|Request|Response|Controller|Service|Repository|Application|Document|Chunk|Entity|Builder|Log)\\b");
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\([^\\n)]*\\)");
    private static final Pattern DOT_METHOD_PATTERN = Pattern.compile("\\b[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\b");
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("[A-Za-z]:(?:\\\\\\\\|/)[A-Za-z0-9_ ./\\\\:-]+");
    private static final Pattern UNIX_PATH_PATTERN = Pattern.compile("(?:/mnt/[A-Za-z0-9_./-]+|src/main/java/[A-Za-z0-9_./-]+)");

    private final EntityStorageService entityStorageService;
    private final EntityPersistenceService entityPersistenceService;

    public EntityExtractionServiceImpl(EntityStorageService entityStorageService,
                                       EntityPersistenceService entityPersistenceService) {
        this.entityStorageService = entityStorageService;
        this.entityPersistenceService = entityPersistenceService;
    }

    @Override
    public List<EntityHit> extractEntities(String text) {
        List<EntityHit> hits = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return hits;
        }

        Map<String, EntityHit> uniqueHits = new LinkedHashMap<>();
        collectApis(uniqueHits, text);
        collectTables(uniqueHits, text);
        collectFields(uniqueHits, text);
        collectConfigs(uniqueHits, text);
        collectServices(uniqueHits, text);
        collectClasses(uniqueHits, text);
        collectMethods(uniqueHits, text);
        collectStatuses(uniqueHits, text);
        collectLibraries(uniqueHits, text);
        collectPaths(uniqueHits, text);

        hits.addAll(uniqueHits.values());
        return hits;
    }

    @Override
    @Transactional
    public void extractAndSave(Long documentId, List<DocumentChunk> chunks) {
        if (documentId == null || chunks == null || chunks.isEmpty()) {
            return;
        }

        log.info("start extract entities, documentId = {}, chunkCount = {}", documentId, chunks.size());

        int linkCount = 0;
        for (DocumentChunk chunk : chunks) {
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }

            List<EntityHit> hits = extractEntities(chunk.getContent());
            // Compatibility strategy for Day 3:
            // if chunk ID is unavailable after save, persist links with CHUNK_ID = null first.
            entityPersistenceService.saveChunkEntities(documentId, chunk.getId(), chunk.getContent(), hits);
            linkCount += hits.size();
        }

        log.info("entity extraction finished, documentId = {}, linkCount = {}", documentId, linkCount);
    }

    @Override
    @Transactional
    public void deleteLinksByDocumentId(Long documentId) {
        entityStorageService.deleteDocumentEntityLinksByDocumentId(documentId);
    }

    private void collectApis(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, API_PATTERN, EntityType.API);
    }

    private void collectTables(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, TABLE_PATTERN, EntityType.TABLE);
    }

    private void collectFields(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, CAMEL_FIELD_PATTERN, EntityType.FIELD);
        addPatternMatches(hits, text, UPPER_FIELD_PATTERN, EntityType.FIELD);
    }

    private void collectConfigs(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, CONFIG_KEY_PATTERN, EntityType.CONFIG);
        for (String value : CONFIG_FILE_VALUES) {
            addLiteralMatch(hits, text, value, EntityType.CONFIG);
        }
    }

    private void collectServices(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, SERVICE_PATTERN, EntityType.SERVICE);
    }

    private void collectClasses(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, CLASS_PATTERN, EntityType.CLASS);
    }

    private void collectMethods(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, METHOD_CALL_PATTERN, EntityType.METHOD);
        addPatternMatches(hits, text, DOT_METHOD_PATTERN, EntityType.METHOD);
    }

    private void collectStatuses(Map<String, EntityHit> hits, String text) {
        for (String value : STATUS_VALUES) {
            addLiteralMatch(hits, text, value, EntityType.STATUS);
        }
    }

    private void collectLibraries(Map<String, EntityHit> hits, String text) {
        for (String value : LIBRARY_VALUES) {
            addLiteralMatch(hits, text, value, EntityType.LIBRARY);
        }
    }

    private void collectPaths(Map<String, EntityHit> hits, String text) {
        addPatternMatches(hits, text, WINDOWS_PATH_PATTERN, EntityType.PATH);
        addPatternMatches(hits, text, UNIX_PATH_PATTERN, EntityType.PATH);
    }

    private void addPatternMatches(Map<String, EntityHit> hits, String text, Pattern pattern, EntityType entityType) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            addHit(hits, matcher.group(), entityType);
        }
    }

    private void addLiteralMatch(Map<String, EntityHit> hits, String text, String literal, EntityType entityType) {
        int start = 0;
        while (start >= 0 && start < text.length()) {
            int index = text.indexOf(literal, start);
            if (index < 0) {
                break;
            }
            addHit(hits, literal, entityType);
            start = index + literal.length();
        }
    }

    private void addHit(Map<String, EntityHit> hits, String sourceText, EntityType entityType) {
        if (!StringUtils.hasText(sourceText) || entityType == null) {
            return;
        }

        String cleaned = cleanupMatch(sourceText);
        if (!StringUtils.hasText(cleaned)) {
            return;
        }

        String normalizedName = cleaned.toLowerCase(Locale.ROOT);
        String dedupKey = entityType.name() + "::" + normalizedName;
        if (hits.containsKey(dedupKey)) {
            return;
        }

        EntityHit hit = new EntityHit();
        hit.setEntityName(cleaned);
        hit.setEntityType(entityType);
        hit.setNormalizedName(normalizedName);
        hit.setSourceText(sourceText);
        hits.put(dedupKey, hit);
    }

    private String cleanupMatch(String value) {
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("[,;:]+$", "");
        return cleaned;
    }
}
