package com.example.rag.service.impl;

import com.example.rag.dto.RelationHit;
import com.example.rag.entity.RagEntityRelation;
import com.example.rag.enums.RelationType;
import com.example.rag.repository.RagEntityRelationRepository;
import com.example.rag.service.RelationStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class RelationStorageServiceImpl implements RelationStorageService {

    private final RagEntityRelationRepository ragEntityRelationRepository;

    public RelationStorageServiceImpl(RagEntityRelationRepository ragEntityRelationRepository) {
        this.ragEntityRelationRepository = ragEntityRelationRepository;
    }

    @Override
    @Transactional
    public RelationHit findOrCreateRelation(Long sourceEntityId,
                                            String sourceEntityName,
                                            String sourceEntityType,
                                            Long targetEntityId,
                                            String targetEntityName,
                                            String targetEntityType,
                                            RelationType relationType,
                                            Long documentId,
                                            Long chunkId,
                                            String evidenceText) {
        boolean exists = ragEntityRelationRepository.existsBySourceEntityIdAndTargetEntityIdAndRelationTypeAndDocumentIdAndChunkId(
                sourceEntityId, targetEntityId, relationType.name(), documentId, chunkId
        );

        if (!exists) {
            RagEntityRelation relation = new RagEntityRelation();
            relation.setSourceEntityId(sourceEntityId);
            relation.setTargetEntityId(targetEntityId);
            relation.setRelationType(relationType.name());
            relation.setDocumentId(documentId);
            relation.setChunkId(chunkId);
            relation.setEvidenceText(evidenceText);
            relation.setCreatedAt(new Date());
            ragEntityRelationRepository.save(relation);
        }

        RelationHit hit = new RelationHit();
        hit.setSourceEntityId(sourceEntityId);
        hit.setSourceEntityName(sourceEntityName);
        hit.setSourceEntityType(sourceEntityType);
        hit.setTargetEntityId(targetEntityId);
        hit.setTargetEntityName(targetEntityName);
        hit.setTargetEntityType(targetEntityType);
        hit.setRelationType(relationType.name());
        hit.setDocumentId(documentId);
        hit.setChunkId(chunkId);
        hit.setEvidenceText(evidenceText);
        return hit;
    }

    @Override
    @Transactional
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        ragEntityRelationRepository.deleteByDocumentId(documentId);
    }
}
