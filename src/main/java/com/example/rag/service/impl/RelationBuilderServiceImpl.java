package com.example.rag.service.impl;

import com.example.rag.dto.EntityHit;
import com.example.rag.dto.RelationBuildResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.enums.RelationType;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentEntityLinkRepository;
import com.example.rag.service.EntityExtractionService;
import com.example.rag.service.EntityStorageService;
import com.example.rag.service.RelationBuilderService;
import com.example.rag.service.RelationStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RelationBuilderServiceImpl implements RelationBuilderService {

    private static final Logger log = LoggerFactory.getLogger(RelationBuilderServiceImpl.class);
    private static final int MAX_RELATED_ENTITIES = 30;
    private static final Pattern DATABASE_COLUMN_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> SQL_KEYWORD_BLACKLIST = new HashSet<>(Arrays.asList(
            "ORDER", "BY", "ASC", "DESC", "SELECT", "FROM", "WHERE", "GROUP", "HAVING",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL",
            "IS", "IN", "AS"
    ));
    private static final Set<String> DOCUMENT_TYPE_BLACKLIST = new HashSet<>(Arrays.asList(
            "PDF", "MD", "JSON", "TXT", "DOC", "DOCX"
    ));

    private final DocumentEntityLinkRepository documentEntityLinkRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EntityExtractionService entityExtractionService;
    private final EntityStorageService entityStorageService;
    private final RelationStorageService relationStorageService;

    public RelationBuilderServiceImpl(DocumentEntityLinkRepository documentEntityLinkRepository,
                                      DocumentChunkRepository documentChunkRepository,
                                      EntityExtractionService entityExtractionService,
                                      EntityStorageService entityStorageService,
                                      RelationStorageService relationStorageService) {
        this.documentEntityLinkRepository = documentEntityLinkRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.entityExtractionService = entityExtractionService;
        this.entityStorageService = entityStorageService;
        this.relationStorageService = relationStorageService;
    }

    @Override
    public RelationBuildResult buildRelationsForChunk(Long documentId, Long chunkId, String chunkText) {
        RelationBuildResult result = new RelationBuildResult();
        result.setDocumentId(documentId);
        result.setChunkId(chunkId);
        result.setChunkCount(1);

        if (documentId == null || chunkId == null) {
            return result;
        }

        List<EntityRef> entities = loadChunkEntities(documentId, chunkId, chunkText);
        result.setEntityCount(entities.size());
        if (entities.size() < 2) {
            return result;
        }

        List<EntityRef> uniqueEntities = deduplicateEntities(entities);
        Map<String, EntityRef> entityMap = buildNormalizedEntityMap(uniqueEntities);

        buildBaseRelations(documentId, chunkId, chunkText, uniqueEntities, result);
        buildCoOccurrenceRelations(documentId, chunkId, chunkText, uniqueEntities, result);
        buildSpecificRelations(documentId, chunkId, chunkText, entityMap, result);
        finalizeResultCounts(result);
        return result;
    }

    @Override
    public RelationBuildResult rebuildRelationsForDocument(Long documentId) {
        RelationBuildResult summary = new RelationBuildResult();
        summary.setDocumentId(documentId);
        relationStorageService.deleteByDocumentId(documentId);

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        summary.setChunkCount(chunks.size());
        int totalEntities = 0;
        int totalRelations = 0;
        int totalTableHasField = 0;
        int totalApiUsesTable = 0;
        int totalMentionedWith = 0;
        List<RelationHit> relations = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            RelationBuildResult chunkResult = buildRelationsForChunk(documentId, chunk.getId(), chunk.getContent());
            totalEntities += chunkResult.getEntityCount();
            totalRelations += chunkResult.getRelationCount();
            totalTableHasField += safeInt(chunkResult.getCreatedTableHasFieldCount());
            totalApiUsesTable += safeInt(chunkResult.getCreatedApiUsesTableCount());
            totalMentionedWith += safeInt(chunkResult.getCreatedMentionedWithCount());
            relations.addAll(chunkResult.getRelations());
        }

        summary.setEntityCount(totalEntities);
        summary.setRelationCount(totalRelations);
        summary.setCreatedTableHasFieldCount(totalTableHasField);
        summary.setCreatedApiUsesTableCount(totalApiUsesTable);
        summary.setCreatedMentionedWithCount(totalMentionedWith);
        summary.setRelations(relations);
        log.info("relation rebuild completed, documentId = {}, chunkCount = {}, entityCount = {}, createdRelationCount = {}, createdTableHasFieldCount = {}, createdApiUsesTableCount = {}, createdMentionedWithCount = {}",
                documentId, summary.getChunkCount(), totalEntities, totalRelations, totalTableHasField, totalApiUsesTable, totalMentionedWith);
        return summary;
    }

    private List<EntityRef> loadChunkEntities(Long documentId, Long chunkId, String chunkText) {
        Map<String, EntityRef> entityMap = new LinkedHashMap<>();

        List<Object[]> rows = documentEntityLinkRepository.findEntitiesByDocumentIdAndChunkId(documentId, chunkId);
        for (Object[] row : rows) {
            if (row == null || row.length < 5 || row[0] == null) {
                continue;
            }
            EntityRef ref = new EntityRef();
            ref.setEntityId(((Number) row[0]).longValue());
            ref.setEntityName(row[1] == null ? null : String.valueOf(row[1]));
            ref.setEntityType(row[2] == null ? null : String.valueOf(row[2]));
            ref.setNormalizedName(row[3] == null ? null : String.valueOf(row[3]).toLowerCase(Locale.ROOT));
            ref.setSourceText(row[4] == null ? null : String.valueOf(row[4]));
            entityMap.putIfAbsent(buildEntityKey(ref.getEntityType(), ref.getNormalizedName()), ref);
        }

        if (StringUtils.hasText(chunkText)) {
            mergeExtractedEntities(documentId, chunkId, chunkText, entityMap);
        }
        return new ArrayList<>(entityMap.values());
    }

    private void buildCoOccurrenceRelations(Long documentId,
                                            Long chunkId,
                                            String chunkText,
                                            List<EntityRef> entities,
                                            RelationBuildResult result) {
        List<EntityRef> filtered = new ArrayList<>();
        for (EntityRef entity : entities) {
            if (!isNoiseEntity(entity)) {
                filtered.add(entity);
            }
            if (filtered.size() >= MAX_RELATED_ENTITIES) {
                break;
            }
        }

        for (int i = 0; i < filtered.size(); i++) {
            for (int j = i + 1; j < filtered.size(); j++) {
                saveRelation(documentId, chunkId, chunkText, filtered.get(i), filtered.get(j), RelationType.MENTIONED_WITH, 0.35d, result);
            }
        }
    }

    private void buildBaseRelations(Long documentId,
                                    Long chunkId,
                                    String chunkText,
                                    List<EntityRef> entities,
                                    RelationBuildResult result) {
        List<EntityRef> apis = filterByType(entities, "API");
        List<EntityRef> tables = filterByType(entities, "TABLE");
        List<EntityRef> fields = filterByType(entities, "FIELD");
        List<EntityRef> services = filterByType(entities, "SERVICE");
        List<EntityRef> classes = filterByType(entities, "CLASS");
        List<EntityRef> methods = filterByType(entities, "METHOD");

        for (EntityRef api : apis) {
            for (EntityRef service : services) {
                saveRelation(documentId, chunkId, chunkText, api, service, RelationType.API_BELONGS_TO_SERVICE, 0.85d, result);
            }
            for (EntityRef table : tables) {
                saveRelation(documentId, chunkId, chunkText, api, table, RelationType.API_USES_TABLE, 0.8d, result);
            }
        }

        for (EntityRef table : tables) {
            for (EntityRef field : fields) {
                if (shouldCreateTableHasFieldRelation(chunkText, table, field)) {
                    saveRelation(documentId, chunkId, chunkText, table, field, RelationType.TABLE_HAS_FIELD, 0.9d, result);
                }
            }
        }

        for (EntityRef service : services) {
            for (EntityRef clazz : classes) {
                saveRelation(documentId, chunkId, chunkText, service, clazz, RelationType.SERVICE_HAS_CLASS, 0.75d, result);
            }
        }

        for (EntityRef clazz : classes) {
            for (EntityRef method : methods) {
                saveRelation(documentId, chunkId, chunkText, clazz, method, RelationType.CLASS_HAS_METHOD, 0.75d, result);
            }
        }

        for (int i = 0; i < services.size(); i++) {
            for (int j = i + 1; j < services.size(); j++) {
                saveRelation(documentId, chunkId, chunkText, services.get(i), services.get(j), RelationType.SERVICE_CALLS_SERVICE, 0.7d, result);
            }
        }
    }

    private void buildSpecificRelations(Long documentId,
                                        Long chunkId,
                                        String chunkText,
                                        Map<String, EntityRef> entityMap,
                                        RelationBuildResult result) {
        for (EntityRef entity : entityMap.values()) {
            if (isService(entity)) {
                for (EntityRef library : entityMap.values()) {
                    if (isLibrary(library)) {
                        saveRelation(documentId, chunkId, chunkText, entity, library, RelationType.USES, 0.8d, result);
                    }
                }
            }
        }

        EntityRef pdf = entityMap.get("pdf");
        EntityRef pdfBox = entityMap.get("pdfbox");
        EntityRef uploadApi = entityMap.get("/upload");
        if (pdfBox != null && containsAnyIgnoreCase(chunkText, "依赖", "使用", "解析")) {
            saveIfPresent(documentId, chunkId, chunkText, pdf, pdfBox, RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, uploadApi, pdfBox, RelationType.DEPENDS_ON, result);
        }

        EntityRef ragDocumentChunks = entityMap.get("rag_document_chunks");
        if (ragDocumentChunks != null) {
            saveIfPresent(documentId, chunkId, chunkText, entityMap.get("embeddingstatus"), ragDocumentChunks, RelationType.BELONGS_TO, result);
            saveIfPresent(documentId, chunkId, chunkText, entityMap.get("chunkindex"), ragDocumentChunks, RelationType.BELONGS_TO, result);
            saveIfPresent(documentId, chunkId, chunkText, entityMap.get("chunkcontent"), ragDocumentChunks, RelationType.BELONGS_TO, result);
            saveIfPresent(documentId, chunkId, chunkText, entityMap.get("tokencount"), ragDocumentChunks, RelationType.BELONGS_TO, result);
        }

        EntityRef appYml = entityMap.get("application.yml");
        EntityRef appProperties = entityMap.get("application.properties");
        saveConfiguredBy(documentId, chunkId, chunkText, entityMap.get("spring.datasource.url"), appYml, appProperties, result);
        saveConfiguredBy(documentId, chunkId, chunkText, entityMap.get("server.port"), appYml, appProperties, result);

        EntityRef documentChunkService = entityMap.get("documentchunkservice");
        if (documentChunkService != null) {
            if (entityMap.containsKey("chunk_size")) {
                saveRelation(documentId, chunkId, chunkText, entityMap.get("chunk_size"), documentChunkService, RelationType.DEFINED_IN, 0.85d, result);
            } else if (containsIgnoreCase(chunkText, "chunk size 500") || containsIgnoreCase(chunkText, "chunk_size")) {
                EntityRef chunk = entityMap.get("chunk");
                if (chunk != null) {
                    saveRelation(documentId, chunkId, chunkText, chunk, documentChunkService, RelationType.DEFINED_IN, 0.7d, result);
                }
            }
        }

        EntityRef searchApi = entityMap.get("/search");
        if (searchApi != null) {
            saveIfPresent(documentId, chunkId, chunkText, searchApi, entityMap.get("searchservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, searchApi, entityMap.get("documentchunkservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, searchApi, entityMap.get("dbms_lob.instr"), RelationType.DEPENDS_ON, result);
        }

        EntityRef askApi = entityMap.get("/ask");
        if (askApi != null) {
            saveIfPresent(documentId, chunkId, chunkText, askApi, entityMap.get("searchservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, askApi, entityMap.get("contextbuilderservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, askApi, entityMap.get("answerbuilderservice"), RelationType.DEPENDS_ON, result);
        }

        EntityRef rebuildChunks = entityMap.get("rebuildchunks");
        if (rebuildChunks != null) {
            saveIfPresent(documentId, chunkId, chunkText, rebuildChunks, entityMap.get("documentchunkservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, rebuildChunks, entityMap.get("entityextractionservice"), RelationType.DEPENDS_ON, result);
            saveIfPresent(documentId, chunkId, chunkText, rebuildChunks, entityMap.get("relationbuilderservice"), RelationType.DEPENDS_ON, result);
        }
    }

    private void saveConfiguredBy(Long documentId,
                                  Long chunkId,
                                  String chunkText,
                                  EntityRef configKey,
                                  EntityRef appYml,
                                  EntityRef appProperties,
                                  RelationBuildResult result) {
        if (configKey == null) {
            return;
        }
        saveIfPresent(documentId, chunkId, chunkText, configKey, appYml, RelationType.CONFIGURED_BY, result);
        saveIfPresent(documentId, chunkId, chunkText, configKey, appProperties, RelationType.CONFIGURED_BY, result);
    }

    private void saveIfPresent(Long documentId,
                               Long chunkId,
                               String chunkText,
                               EntityRef source,
                               EntityRef target,
                               RelationType relationType,
                               RelationBuildResult result) {
        saveIfPresent(documentId, chunkId, chunkText, source, target, relationType, defaultConfidence(relationType), result);
    }

    private void saveIfPresent(Long documentId,
                               Long chunkId,
                               String chunkText,
                               EntityRef source,
                               EntityRef target,
                               RelationType relationType,
                               Double confidence,
                               RelationBuildResult result) {
        if (source == null || target == null) {
            return;
        }
        saveRelation(documentId, chunkId, chunkText, source, target, relationType, confidence, result);
    }

    private void saveRelation(Long documentId,
                              Long chunkId,
                              String chunkText,
                              EntityRef source,
                              EntityRef target,
                              RelationType relationType,
                              RelationBuildResult result) {
        saveRelation(documentId, chunkId, chunkText, source, target, relationType, defaultConfidence(relationType), result);
    }

    private void saveRelation(Long documentId,
                              Long chunkId,
                              String chunkText,
                              EntityRef source,
                              EntityRef target,
                              RelationType relationType,
                              Double confidence,
                              RelationBuildResult result) {
        if (hasLocalRelation(result, source, target, relationType)) {
            return;
        }
        try {
            RelationHit relation = relationStorageService.findOrCreateRelation(
                    source.getEntityId(),
                    source.getEntityName(),
                    source.getEntityType(),
                    target.getEntityId(),
                    target.getEntityName(),
                    target.getEntityType(),
                    relationType,
                    documentId,
                    chunkId,
                    buildEvidenceText(chunkText, source, target, relationType),
                    confidence
            );
            result.getRelations().add(relation);
        } catch (RuntimeException ex) {
            log.error("build single relation failed, documentId = {}, chunkId = {}, source = {}, target = {}, relationType = {}",
                    documentId, chunkId, source.getNormalizedName(), target.getNormalizedName(), relationType.name(), ex);
        }
    }

    private boolean hasLocalRelation(RelationBuildResult result,
                                     EntityRef source,
                                     EntityRef target,
                                     RelationType relationType) {
        if (result == null || source == null || target == null || relationType == null) {
            return false;
        }
        for (RelationHit relation : result.getRelations()) {
            if (relation == null) {
                continue;
            }
            if (source.getEntityId() != null
                    && source.getEntityId().equals(relation.getSourceEntityId())
                    && target.getEntityId() != null
                    && target.getEntityId().equals(relation.getTargetEntityId())
                    && relationType.name().equals(relation.getRelationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNoiseEntity(EntityRef entity) {
        if (entity == null || !StringUtils.hasText(entity.getEntityType())) {
            return true;
        }
        String type = entity.getEntityType();
        return "API".equals(type)
                || "TABLE".equals(type)
                || "FIELD".equals(type)
                || "CONFIG".equals(type)
                || "SERVICE".equals(type)
                || "CLASS".equals(type)
                || "METHOD".equals(type)
                || "STATUS".equals(type)
                || "LIBRARY".equals(type)
                || "PATH".equals(type);
    }

    private boolean isService(EntityRef entity) {
        return entity != null && "SERVICE".equals(entity.getEntityType());
    }

    private boolean isLibrary(EntityRef entity) {
        return entity != null && "LIBRARY".equals(entity.getEntityType());
    }

    private boolean shouldCreateTableHasFieldRelation(String chunkText, EntityRef table, EntityRef field) {
        if (table == null || field == null) {
            return false;
        }
        if (!"TABLE".equals(normalizeEntityType(table.getEntityType()))) {
            return false;
        }
        if (!"FIELD".equals(normalizeEntityType(field.getEntityType()))) {
            return false;
        }
        if (!StringUtils.hasText(table.getNormalizedName()) || !StringUtils.hasText(field.getNormalizedName())) {
            return false;
        }
        if (table.getNormalizedName().equals(field.getNormalizedName())) {
            return false;
        }
        if (!isTableSchemaChunk(chunkText)) {
            return false;
        }
        if (!isDatabaseColumnName(field.getEntityName())) {
            return false;
        }
        return isLikelySchemaFieldMention(chunkText, field.getEntityName());
    }

    private boolean isTableSchemaChunk(String chunkText) {
        if (!StringUtils.hasText(chunkText)) {
            return false;
        }
        String upper = chunkText.toUpperCase(Locale.ROOT);
        String lower = chunkText.toLowerCase(Locale.ROOT);
        return upper.contains("CREATE TABLE")
                || upper.contains("COLUMN_NAME")
                || upper.contains("USER_TAB_COLUMNS")
                || chunkText.contains("表结构")
                || chunkText.contains("字段列表")
                || chunkText.contains("表字段包括")
                || chunkText.contains("字段包括")
                || containsAny(lower, "number", "varchar2", "date", "char", "clob");
    }

    private boolean isDatabaseColumnName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return DATABASE_COLUMN_PATTERN.matcher(normalized).matches()
                && !SQL_KEYWORD_BLACKLIST.contains(normalized)
                && !DOCUMENT_TYPE_BLACKLIST.contains(normalized);
    }

    private boolean isLikelySchemaFieldMention(String chunkText, String fieldName) {
        if (!StringUtils.hasText(chunkText) || !StringUtils.hasText(fieldName)) {
            return false;
        }
        String upper = chunkText.toUpperCase(Locale.ROOT);
        String normalizedField = fieldName.trim().toUpperCase(Locale.ROOT);

        if (upper.contains("CREATE TABLE") && upper.contains(normalizedField)) {
            return true;
        }
        if ((upper.contains("USER_TAB_COLUMNS") || upper.contains("COLUMN_NAME")) && upper.contains(normalizedField)) {
            return true;
        }
        if (containsFieldListContext(upper, normalizedField, "字段包括")) {
            return true;
        }
        if (containsFieldListContext(upper, normalizedField, "常见字段包括")) {
            return true;
        }
        if (containsFieldListContext(upper, normalizedField, "表字段包括")) {
            return true;
        }
        return false;
    }

    private boolean containsFieldListContext(String upperChunkText, String fieldName, String marker) {
        int markerIndex = upperChunkText.indexOf(marker.toUpperCase(Locale.ROOT));
        if (markerIndex < 0) {
            return false;
        }
        String tail = upperChunkText.substring(markerIndex);
        int end = findFieldListEnd(tail);
        String fieldSection = end >= 0 ? tail.substring(0, end) : tail;
        return fieldSection.contains(fieldName);
    }

    private int findFieldListEnd(String text) {
        int sentenceEnd = text.indexOf('。');
        int newlineEnd = text.indexOf('\n');
        int semicolonEnd = text.indexOf('；');
        int englishSemicolonEnd = text.indexOf(';');
        int min = Integer.MAX_VALUE;
        for (int end : new int[]{sentenceEnd, newlineEnd, semicolonEnd, englishSemicolonEnd}) {
            if (end >= 0 && end < min) {
                min = end;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private void mergeExtractedEntities(Long documentId,
                                        Long chunkId,
                                        String chunkText,
                                        Map<String, EntityRef> entityMap) {
        List<EntityHit> extractedEntities = entityExtractionService.extractEntities(chunkText);
        for (EntityHit extracted : extractedEntities) {
            if (extracted == null || extracted.getEntityType() == null || !StringUtils.hasText(extracted.getNormalizedName())) {
                continue;
            }

            String entityType = normalizeEntityType(extracted.getEntityType().name());
            String normalizedName = extracted.getNormalizedName().toLowerCase(Locale.ROOT);
            String key = buildEntityKey(entityType, normalizedName);
            if (entityMap.containsKey(key)) {
                continue;
            }

            Long entityId = entityStorageService.findOrCreateEntity(
                    extracted.getEntityName(),
                    entityType,
                    normalizedName
            );
            entityStorageService.insertDocumentEntityLink(
                    documentId,
                    chunkId,
                    entityId,
                    StringUtils.hasText(extracted.getSourceText()) ? extracted.getSourceText() : chunkText
            );

            EntityRef ref = new EntityRef();
            ref.setEntityId(entityId);
            ref.setEntityName(extracted.getEntityName());
            ref.setEntityType(entityType);
            ref.setNormalizedName(normalizedName);
            ref.setSourceText(extracted.getSourceText());
            entityMap.put(key, ref);
        }
    }

    private List<EntityRef> filterByType(List<EntityRef> entities, String entityType) {
        List<EntityRef> filtered = new ArrayList<>();
        if (entities == null || !StringUtils.hasText(entityType)) {
            return filtered;
        }
        for (EntityRef entity : entities) {
            if (entity != null && entityType.equals(normalizeEntityType(entity.getEntityType()))) {
                filtered.add(entity);
            }
        }
        return filtered;
    }

    private List<EntityRef> deduplicateEntities(List<EntityRef> entities) {
        Map<String, EntityRef> dedup = new LinkedHashMap<>();
        if (entities == null) {
            return new ArrayList<>();
        }
        for (EntityRef entity : entities) {
            if (entity == null || !StringUtils.hasText(entity.getNormalizedName())) {
                continue;
            }
            dedup.putIfAbsent(buildEntityKey(entity.getEntityType(), entity.getNormalizedName()), entity);
        }
        return new ArrayList<>(dedup.values());
    }

    private Map<String, EntityRef> buildNormalizedEntityMap(List<EntityRef> entities) {
        Map<String, EntityRef> entityMap = new LinkedHashMap<>();
        if (entities == null) {
            return entityMap;
        }
        for (EntityRef entity : entities) {
            if (entity != null && StringUtils.hasText(entity.getNormalizedName())) {
                entityMap.putIfAbsent(entity.getNormalizedName(), entity);
            }
        }
        return entityMap;
    }

    private String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        if ("COLUMN".equalsIgnoreCase(entityType)) {
            return "FIELD";
        }
        return entityType.toUpperCase(Locale.ROOT);
    }

    private String buildEntityKey(String entityType, String normalizedName) {
        return normalizeEntityType(entityType) + "|" + (normalizedName == null ? "" : normalizedName.toLowerCase(Locale.ROOT));
    }

    private void finalizeResultCounts(RelationBuildResult result) {
        int tableHasField = 0;
        int apiUsesTable = 0;
        int mentionedWith = 0;
        for (RelationHit relation : result.getRelations()) {
            if (relation == null || !StringUtils.hasText(relation.getRelationType())) {
                continue;
            }
            if (RelationType.TABLE_HAS_FIELD.name().equals(relation.getRelationType())) {
                tableHasField++;
            } else if (RelationType.API_USES_TABLE.name().equals(relation.getRelationType())) {
                apiUsesTable++;
            } else if (RelationType.MENTIONED_WITH.name().equals(relation.getRelationType())) {
                mentionedWith++;
            }
        }
        result.setRelationCount(result.getRelations().size());
        result.setCreatedTableHasFieldCount(tableHasField);
        result.setCreatedApiUsesTableCount(apiUsesTable);
        result.setCreatedMentionedWithCount(mentionedWith);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Double defaultConfidence(RelationType relationType) {
        if (relationType == null) {
            return 0.5d;
        }
        switch (relationType) {
            case TABLE_HAS_FIELD:
            case API_BELONGS_TO_SERVICE:
                return 0.9d;
            case API_USES_TABLE:
            case SERVICE_HAS_CLASS:
            case CLASS_HAS_METHOD:
            case SERVICE_CALLS_SERVICE:
            case USES:
            case BELONGS_TO:
            case CONFIGURED_BY:
            case DEPENDS_ON:
            case DEFINED_IN:
                return 0.8d;
            case MENTIONED_WITH:
            case RELATED_TO:
                return 0.35d;
            default:
                return 0.6d;
        }
    }

    private boolean containsIgnoreCase(String text, String token) {
        return StringUtils.hasText(text) && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private boolean containsAnyIgnoreCase(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String token : tokens) {
            if (containsIgnoreCase(text, token)) {
                return true;
            }
        }
        return false;
    }

    private String truncateEvidence(String chunkText) {
        if (!StringUtils.hasText(chunkText)) {
            return "";
        }
        String normalized = chunkText.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }

    private String buildEvidenceText(String chunkText,
                                     EntityRef source,
                                     EntityRef target,
                                     RelationType relationType) {
        String fallback = truncateEvidence(chunkText);
        if (!StringUtils.hasText(chunkText)) {
            return fallback;
        }

        List<String> sentences = splitSentences(chunkText);
        if (sentences.isEmpty()) {
            return fallback;
        }

        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int score = scoreSentence(sentence, source, target, relationType);
            if (score > 0) {
                scored.add(new ScoredSentence(sentence, score, i));
            }
        }

        if (!scored.isEmpty()) {
            scored.sort(Comparator
                    .comparingInt(ScoredSentence::getScore).reversed()
                    .thenComparingInt(ScoredSentence::getIndex));
            return clipEvidence(scored.get(0).getText());
        }

        int sourceIndex = findSentenceIndex(sentences, source == null ? null : source.getEntityName());
        int targetIndex = findSentenceIndex(sentences, target == null ? null : target.getEntityName());
        if (sourceIndex >= 0 || targetIndex >= 0) {
            String combined = combineAdjacentSentences(sentences, sourceIndex, targetIndex);
            if (StringUtils.hasText(combined)) {
                return clipEvidence(combined);
            }
        }

        return fallback;
    }

    private List<String> splitSentences(String chunkText) {
        List<String> sentences = new ArrayList<>();
        if (!StringUtils.hasText(chunkText)) {
            return sentences;
        }

        String[] parts = chunkText.replace("\r", "\n").split("[。；;\\n]|(?<=[A-Za-z0-9_])\\.(?=\\s|$)");
        for (String part : parts) {
            String normalized = part == null ? "" : part.replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(normalized)) {
                sentences.add(normalized);
            }
        }
        return sentences;
    }

    private int scoreSentence(String sentence,
                              EntityRef source,
                              EntityRef target,
                              RelationType relationType) {
        if (!StringUtils.hasText(sentence)) {
            return 0;
        }

        String lower = sentence.toLowerCase(Locale.ROOT);
        int score = 0;
        boolean hasSource = containsEntity(sentence, source);
        boolean hasTarget = containsEntity(sentence, target);

        if (hasSource) {
            score += 4;
        }
        if (hasTarget) {
            score += 4;
        }
        if (hasSource && hasTarget) {
            score += 10;
        }

        if (relationType == RelationType.BELONGS_TO && containsAny(lower, "属于", "字段", "表")) {
            score += 6;
        }
        if (relationType == RelationType.USES && containsAny(lower, "使用", "依赖")) {
            score += 6;
        }
        if (relationType == RelationType.DEPENDS_ON && containsAny(lower, "依赖", "调用")) {
            score += 6;
        }
        if (containsAny(lower, "document_id desc", "chunk_index asc", "order by", "dbms_lob.instr")) {
            score += 8;
        }

        return score;
    }

    private int findSentenceIndex(List<String> sentences, String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return -1;
        }
        for (int i = 0; i < sentences.size(); i++) {
            if (containsIgnoreCase(sentences.get(i), entityName)) {
                return i;
            }
        }
        return -1;
    }

    private String combineAdjacentSentences(List<String> sentences, int sourceIndex, int targetIndex) {
        int from = Integer.MAX_VALUE;
        int to = Integer.MIN_VALUE;
        if (sourceIndex >= 0) {
            from = Math.min(from, sourceIndex);
            to = Math.max(to, sourceIndex);
        }
        if (targetIndex >= 0) {
            from = Math.min(from, targetIndex);
            to = Math.max(to, targetIndex);
        }
        if (from == Integer.MAX_VALUE) {
            return "";
        }

        from = Math.max(0, from);
        to = Math.min(sentences.size() - 1, Math.max(to, from));
        StringBuilder builder = new StringBuilder();
        for (int i = from; i <= to; i++) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(sentences.get(i));
        }
        return builder.toString().trim();
    }

    private boolean containsEntity(String sentence, EntityRef entity) {
        if (entity == null) {
            return false;
        }
        return containsIgnoreCase(sentence, entity.getEntityName())
                || containsIgnoreCase(sentence, entity.getNormalizedName())
                || containsIgnoreCase(sentence, entity.getSourceText());
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String token : tokens) {
            if (StringUtils.hasText(token) && text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String clipEvidence(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }

    private static class ScoredSentence {
        private final String text;
        private final int score;
        private final int index;

        private ScoredSentence(String text, int score, int index) {
            this.text = text;
            this.score = score;
            this.index = index;
        }

        public String getText() {
            return text;
        }

        public int getScore() {
            return score;
        }

        public int getIndex() {
            return index;
        }
    }

    private static class EntityRef {
        private Long entityId;
        private String entityName;
        private String entityType;
        private String normalizedName;
        private String sourceText;

        public Long getEntityId() {
            return entityId;
        }

        public void setEntityId(Long entityId) {
            this.entityId = entityId;
        }

        public String getEntityName() {
            return entityName;
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public String getEntityType() {
            return entityType;
        }

        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }

        public String getNormalizedName() {
            return normalizedName;
        }

        public void setNormalizedName(String normalizedName) {
            this.normalizedName = normalizedName;
        }

        public String getSourceText() {
            return sourceText;
        }

        public void setSourceText(String sourceText) {
            this.sourceText = sourceText;
        }
    }
}
