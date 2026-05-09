package com.example.rag.service;

import com.example.rag.dto.RelationHit;
import com.example.rag.enums.RelationType;

public interface RelationStorageService {

    RelationHit findOrCreateRelation(Long sourceEntityId,
                                     String sourceEntityName,
                                     String sourceEntityType,
                                     Long targetEntityId,
                                     String targetEntityName,
                                     String targetEntityType,
                                     RelationType relationType,
                                     Long documentId,
                                     Long chunkId,
                                     String evidenceText,
                                     Double confidence);

    void deleteByDocumentId(Long documentId);
}
