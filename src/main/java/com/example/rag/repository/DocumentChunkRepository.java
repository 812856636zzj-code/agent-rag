package com.example.rag.repository;

import com.example.rag.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    List<DocumentChunk> findByIdIn(List<Long> ids);

    void deleteByDocumentId(Long documentId);

    @Query(value = "select * from RAG_DOCUMENT_CHUNKS " +
            "where dbms_lob.instr(CONTENT, :keyword) > 0 " +
            "order by DOCUMENT_ID desc, CHUNK_INDEX asc", nativeQuery = true)
    List<DocumentChunk> searchByKeyword(@Param("keyword") String keyword);

    @Query(value = "select * from (" +
            "select * from RAG_DOCUMENT_CHUNKS " +
            "where dbms_lob.instr(CONTENT, :keyword) > 0 " +
            "order by DOCUMENT_ID desc, CHUNK_INDEX asc" +
            ") where rownum <= 5", nativeQuery = true)
    List<DocumentChunk> searchTop5ByKeyword(@Param("keyword") String keyword);

    List<DocumentChunk> findByEmbeddingStatusOrderByDocumentIdDescChunkIndexAsc(String embeddingStatus);

    @Modifying
    @Query("update DocumentChunk c set c.embeddingStatus = :embeddingStatus where c.documentId = :documentId")
    int updateEmbeddingStatusByDocumentId(@Param("documentId") Long documentId,
                                          @Param("embeddingStatus") String embeddingStatus);
}
