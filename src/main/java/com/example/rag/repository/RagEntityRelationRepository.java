package com.example.rag.repository;

import com.example.rag.entity.RagEntityRelation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagEntityRelationRepository extends JpaRepository<RagEntityRelation, Long>, RagEntityRelationRepositoryCustom {

    boolean existsBySourceEntityIdAndTargetEntityIdAndRelationTypeAndDocumentIdAndChunkId(Long sourceEntityId,
                                                                                           Long targetEntityId,
                                                                                           String relationType,
                                                                                           Long documentId,
                                                                                           Long chunkId);

    void deleteByDocumentId(Long documentId);

    java.util.List<RagEntityRelation> findBySourceEntityIdOrTargetEntityId(Long sourceEntityId, Long targetEntityId);
}
