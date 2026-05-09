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

    @Query(value = "select c.* from RAG_DOCUMENT_CHUNKS c " +
            "join RAG_DOCUMENTS d on d.ID = c.DOCUMENT_ID " +
            "where d.DOCUMENT_TYPE = 'KNOWLEDGE' " +
            "and dbms_lob.instr(c.CONTENT, :keyword) > 0 " +
            "order by c.DOCUMENT_ID desc, c.CHUNK_INDEX asc", nativeQuery = true)
    List<DocumentChunk> searchByKeyword(@Param("keyword") String keyword);

    @Query(value = "select * from (" +
            "select c.* from RAG_DOCUMENT_CHUNKS c " +
            "join RAG_DOCUMENTS d on d.ID = c.DOCUMENT_ID " +
            "where d.DOCUMENT_TYPE = 'KNOWLEDGE' " +
            "and dbms_lob.instr(c.CONTENT, :keyword) > 0 " +
            "order by c.DOCUMENT_ID desc, c.CHUNK_INDEX asc" +
            ") where rownum <= 5", nativeQuery = true)
    List<DocumentChunk> searchTop5ByKeyword(@Param("keyword") String keyword);

    List<DocumentChunk> findByEmbeddingStatusOrderByDocumentIdDescChunkIndexAsc(String embeddingStatus);

    @Modifying
    @Query("update DocumentChunk c set c.embeddingStatus = :embeddingStatus where c.documentId = :documentId")
    int updateEmbeddingStatusByDocumentId(@Param("documentId") Long documentId,
                                          @Param("embeddingStatus") String embeddingStatus);
}
