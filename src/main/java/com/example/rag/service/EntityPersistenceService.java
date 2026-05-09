package com.example.rag.service;

import com.example.rag.dto.EntityHit;

import java.util.List;

public interface EntityPersistenceService {

    void saveChunkEntities(Long documentId, Long chunkId, String chunkText, List<EntityHit> entities);

    void deleteLinksByDocumentId(Long documentId);
}
