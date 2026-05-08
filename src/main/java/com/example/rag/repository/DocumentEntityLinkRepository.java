package com.example.rag.repository;

import com.example.rag.entity.DocumentEntityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentEntityLinkRepository extends JpaRepository<DocumentEntityLink, Long> {

    void deleteByDocumentId(Long documentId);

    boolean existsByDocumentIdAndChunkIdAndEntityId(Long documentId, Long chunkId, Long entityId);

    @Query(value = "select * from (" +
            "select e.ENTITY_NAME, e.ENTITY_TYPE, e.NORMALIZED_NAME, l.DOCUMENT_ID, l.CHUNK_ID, l.SOURCE_TEXT, l.CREATED_AT " +
            "from RAG_DOCUMENT_ENTITY_LINKS l " +
            "join RAG_ENTITIES e on l.ENTITY_ID = e.ID " +
            "order by l.CREATED_AT desc" +
            ") where rownum <= :limit", nativeQuery = true)
    List<Object[]> findRecentEntityLinks(@Param("limit") int limit);

    @Query(value = "select e.ENTITY_NAME, e.ENTITY_TYPE, e.NORMALIZED_NAME, l.DOCUMENT_ID, l.CHUNK_ID, l.SOURCE_TEXT, l.CREATED_AT " +
            "from RAG_DOCUMENT_ENTITY_LINKS l " +
            "join RAG_ENTITIES e on l.ENTITY_ID = e.ID " +
            "where l.DOCUMENT_ID = :documentId " +
            "order by l.CREATED_AT desc", nativeQuery = true)
    List<Object[]> findEntityLinksByDocumentId(@Param("documentId") Long documentId);

    @Query(value = "select l.CHUNK_ID, count(distinct e.NORMALIZED_NAME) " +
            "from RAG_DOCUMENT_ENTITY_LINKS l " +
            "join RAG_ENTITIES e on l.ENTITY_ID = e.ID " +
            "where l.CHUNK_ID is not null " +
            "and e.NORMALIZED_NAME in (:normalizedNames) " +
            "group by l.CHUNK_ID", nativeQuery = true)
    List<Object[]> countEntityMatchesByNormalizedNames(@Param("normalizedNames") List<String> normalizedNames);
}
