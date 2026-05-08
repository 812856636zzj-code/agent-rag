package com.example.rag.service;

public interface EntityStorageService {

    Long findEntityIdByTypeAndNormalizedName(String entityType, String normalizedName);

    Long insertEntity(String entityName, String entityType, String normalizedName);

    Long findOrCreateEntity(String entityName, String entityType, String normalizedName);

    void insertDocumentEntityLink(Long documentId, Long chunkId, Long entityId, String sourceText);

    void deleteDocumentEntityLinksByDocumentId(Long documentId);
}
