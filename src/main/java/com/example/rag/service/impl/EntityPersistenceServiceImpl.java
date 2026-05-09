package com.example.rag.service.impl;

import com.example.rag.dto.EntityHit;
import com.example.rag.service.EntityPersistenceService;
import com.example.rag.service.EntityStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class EntityPersistenceServiceImpl implements EntityPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EntityPersistenceServiceImpl.class);

    private final EntityStorageService entityStorageService;

    public EntityPersistenceServiceImpl(EntityStorageService entityStorageService) {
        this.entityStorageService = entityStorageService;
    }

    @Override
    public void saveChunkEntities(Long documentId, Long chunkId, String chunkText, List<EntityHit> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (EntityHit entity : entities) {
            if (entity == null || entity.getEntityType() == null || !StringUtils.hasText(entity.getNormalizedName())) {
                continue;
            }

            try {
                Long entityId = entityStorageService.findOrCreateEntity(
                        entity.getEntityName(),
                        entity.getEntityType().name(),
                        entity.getNormalizedName()
                );
                log.info("persist entity, documentId = {}, chunkId = {}, entityName = {}, entityType = {}, entityId = {}",
                        documentId, chunkId, entity.getEntityName(), entity.getEntityType().name(), entityId);
                entityStorageService.insertDocumentEntityLink(
                        documentId,
                        chunkId,
                        entityId,
                        StringUtils.hasText(entity.getSourceText()) ? entity.getSourceText() : chunkText
                );
            } catch (RuntimeException ex) {
                log.error("save chunk entity failed, documentId = {}, chunkId = {}, entityName = {}, entityType = {}",
                        documentId, chunkId, entity.getEntityName(), entity.getEntityType().name(), ex);
            }
        }
    }

    @Override
    public void deleteLinksByDocumentId(Long documentId) {
        entityStorageService.deleteDocumentEntityLinksByDocumentId(documentId);
    }
}
