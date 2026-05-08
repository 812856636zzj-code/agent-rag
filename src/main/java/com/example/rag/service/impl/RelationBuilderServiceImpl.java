package com.example.rag.service.impl;

import com.example.rag.dto.RelationBuildResult;
import com.example.rag.dto.RelationHit;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.enums.RelationType;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentEntityLinkRepository;
import com.example.rag.service.RelationBuilderService;
import com.example.rag.service.RelationStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RelationBuilderServiceImpl implements RelationBuilderService {

    private static final Logger log = LoggerFactory.getLogger(RelationBuilderServiceImpl.class);
    private static final int MAX_RELATED_ENTITIES = 30;

    private final DocumentEntityLinkRepository documentEntityLinkRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RelationStorageService relationStorageService;

    public RelationBuilderServiceImpl(DocumentEntityLinkRepository documentEntityLinkRepository,
                                      DocumentChunkRepository documentChunkRepository,
                                      RelationStorageService relationStorageService) {
        this.documentEntityLinkRepository = documentEntityLinkRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.relationStorageService = relationStorageService;
    }

    @Override
    public RelationBuildResult buildRelationsForChunk(Long documentId, Long chunkId, String chunkText) {
        RelationBuildResult result = new RelationBuildResult();
        result.setDocumentId(documentId);
        result.setChunkId(chunkId);

        if (documentId == null || chunkId == null) {
            return result;
        }

        List<EntityRef> entities = loadChunkEntities(documentId, chunkId);
        result.setEntityCount(entities.size());
        if (entities.size() < 2) {
            return result;
        }

        Map<String, EntityRef> entityMap = new LinkedHashMap<>();
        for (EntityRef entity : entities) {
            entityMap.putIfAbsent(entity.getNormalizedName(), entity);
        }

        buildCoOccurrenceRelations(documentId, chunkId, chunkText, new ArrayList<>(entityMap.values()), result);
        buildSpecificRelations(documentId, chunkId, chunkText, entityMap, result);
        result.setRelationCount(result.getRelations().size());
        return result;
    }

    @Override
    public RelationBuildResult rebuildRelationsForDocument(Long documentId) {
        RelationBuildResult summary = new RelationBuildResult();
        summary.setDocumentId(documentId);
        relationStorageService.deleteByDocumentId(documentId);

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        int totalEntities = 0;
        int totalRelations = 0;
        List<RelationHit> relations = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            RelationBuildResult chunkResult = buildRelationsForChunk(documentId, chunk.getId(), chunk.getContent());
            totalEntities += chunkResult.getEntityCount();
            totalRelations += chunkResult.getRelationCount();
            relations.addAll(chunkResult.getRelations());
        }

        summary.setEntityCount(totalEntities);
        summary.setRelationCount(totalRelations);
        summary.setRelations(relations);
        return summary;
    }

    private List<EntityRef> loadChunkEntities(Long documentId, Long chunkId) {
        List<Object[]> rows = documentEntityLinkRepository.findEntitiesByDocumentIdAndChunkId(documentId, chunkId);
        List<EntityRef> entities = new ArrayList<>();
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
            entities.add(ref);
        }
        return entities;
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
                saveRelation(documentId, chunkId, chunkText, filtered.get(i), filtered.get(j), RelationType.RELATED_TO, result);
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
                        saveRelation(documentId, chunkId, chunkText, entity, library, RelationType.USES, result);
                    }
                }
            }
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
                saveRelation(documentId, chunkId, chunkText, entityMap.get("chunk_size"), documentChunkService, RelationType.DEFINED_IN, result);
            } else if (containsIgnoreCase(chunkText, "chunk size 500") || containsIgnoreCase(chunkText, "chunk_size")) {
                EntityRef chunk = entityMap.get("chunk");
                if (chunk != null) {
                    saveRelation(documentId, chunkId, chunkText, chunk, documentChunkService, RelationType.DEFINED_IN, result);
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
        if (source == null || target == null) {
            return;
        }
        saveRelation(documentId, chunkId, chunkText, source, target, relationType, result);
    }

    private void saveRelation(Long documentId,
                              Long chunkId,
                              String chunkText,
                              EntityRef source,
                              EntityRef target,
                              RelationType relationType,
                              RelationBuildResult result) {
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
                    truncateEvidence(chunkText)
            );
            result.getRelations().add(relation);
        } catch (RuntimeException ex) {
            log.error("build single relation failed, documentId = {}, chunkId = {}, source = {}, target = {}, relationType = {}",
                    documentId, chunkId, source.getNormalizedName(), target.getNormalizedName(), relationType.name(), ex);
        }
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

    private boolean containsIgnoreCase(String text, String token) {
        return StringUtils.hasText(text) && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
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
