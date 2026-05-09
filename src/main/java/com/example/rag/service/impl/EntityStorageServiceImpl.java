package com.example.rag.service.impl;

import com.example.rag.entity.DocumentEntityLink;
import com.example.rag.entity.RagEntity;
import com.example.rag.repository.DocumentEntityLinkRepository;
import com.example.rag.repository.RagEntityRepository;
import com.example.rag.service.EntityStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.Optional;

@Service
public class EntityStorageServiceImpl implements EntityStorageService {

    private final RagEntityRepository ragEntityRepository;
    private final DocumentEntityLinkRepository documentEntityLinkRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public EntityStorageServiceImpl(RagEntityRepository ragEntityRepository,
                                    DocumentEntityLinkRepository documentEntityLinkRepository) {
        this.ragEntityRepository = ragEntityRepository;
        this.documentEntityLinkRepository = documentEntityLinkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Long findEntityIdByTypeAndNormalizedName(String entityType, String normalizedName) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(normalizedName)) {
            return null;
        }

        Optional<RagEntity> optional = ragEntityRepository.findByEntityTypeAndNormalizedName(entityType, normalizedName);
        return optional.map(RagEntity::getId).orElse(null);
    }

    @Override
    @Transactional
    public Long insertEntity(String entityName, String entityType, String normalizedName) {
        Long entityId = nextSequenceValue("RAG_ENTITIES_SEQ");
        entityManager.createNativeQuery(
                        "insert into RAG_ENTITIES (ID, ENTITY_NAME, ENTITY_TYPE, NORMALIZED_NAME, CREATED_AT) " +
                                "values (:id, :entityName, :entityType, :normalizedName, :createdAt)")
                .setParameter("id", entityId)
                .setParameter("entityName", entityName)
                .setParameter("entityType", entityType)
                .setParameter("normalizedName", normalizedName)
                .setParameter("createdAt", new Date())
                .executeUpdate();
        return entityId;
    }

    @Override
    @Transactional
    public Long findOrCreateEntity(String entityName, String entityType, String normalizedName) {
        Long existingId = findEntityIdByTypeAndNormalizedName(entityType, normalizedName);
        if (existingId != null) {
            return existingId;
        }
        try {
            return insertEntity(entityName, entityType, normalizedName);
        } catch (RuntimeException ex) {
            Long retryId = findEntityIdByTypeAndNormalizedName(entityType, normalizedName);
            if (retryId != null) {
                return retryId;
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void insertDocumentEntityLink(Long documentId, Long chunkId, Long entityId, String sourceText) {
        if (documentId == null || entityId == null) {
            return;
        }

        if (chunkId != null && documentEntityLinkRepository.existsByDocumentIdAndChunkIdAndEntityId(documentId, chunkId, entityId)) {
            return;
        }

        Long linkId = nextSequenceValue("RAG_DOCUMENT_ENTITY_LINKS_SEQ");
        entityManager.createNativeQuery(
                        "insert into RAG_DOCUMENT_ENTITY_LINKS (ID, DOCUMENT_ID, CHUNK_ID, ENTITY_ID, SOURCE_TEXT, CREATED_AT) " +
                                "values (:id, :documentId, :chunkId, :entityId, :sourceText, :createdAt)")
                .setParameter("id", linkId)
                .setParameter("documentId", documentId)
                .setParameter("chunkId", chunkId)
                .setParameter("entityId", entityId)
                .setParameter("sourceText", sourceText)
                .setParameter("createdAt", new Date())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteDocumentEntityLinksByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        documentEntityLinkRepository.deleteByDocumentId(documentId);
    }

    private Long nextSequenceValue(String sequenceName) {
        Object value = entityManager.createNativeQuery("select " + sequenceName + ".NEXTVAL from dual")
                .getSingleResult();
        return ((Number) value).longValue();
    }
}
