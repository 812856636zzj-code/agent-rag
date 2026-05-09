package com.example.rag.repository;

import com.example.rag.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RagEntityRepository extends JpaRepository<RagEntity, Long> {

    Optional<RagEntity> findByEntityTypeAndNormalizedName(String entityType, String normalizedName);

    @Query(value = "select * from RAG_ENTITIES e " +
            "where lower(e.NORMALIZED_NAME) = lower(:keyword) " +
            "   or lower(e.ENTITY_NAME) = lower(:keyword) " +
            "   or lower(e.NORMALIZED_NAME) like lower(:likeKeyword) " +
            "   or lower(e.ENTITY_NAME) like lower(:likeKeyword) " +
            "order by e.ENTITY_TYPE, e.NORMALIZED_NAME", nativeQuery = true)
    List<RagEntity> searchEntityCandidates(@Param("keyword") String keyword,
                                           @Param("likeKeyword") String likeKeyword);
}
