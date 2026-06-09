package com.example.rag.repository;

import com.example.rag.entity.RagAgentMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RagAgentMemoryRepository extends JpaRepository<RagAgentMemory, Long> {

    @Query(value = "select m from RagAgentMemory m " +
            "where m.memoryType = :memoryType " +
            "and m.memoryKey = :memoryKey " +
            "and ((:entityName is null and m.entityName is null) or m.entityName = :entityName) " +
            "and ((:failureType is null and m.failureType is null) or m.failureType = :failureType)")
    List<RagAgentMemory> findDuplicates(@Param("memoryType") String memoryType,
                                        @Param("memoryKey") String memoryKey,
                                        @Param("entityName") String entityName,
                                        @Param("failureType") String failureType);

    @Query(value = "select m from RagAgentMemory m " +
            "where (:intent is not null and m.intent = :intent) " +
            "or (:failureType is not null and m.failureType = :failureType) " +
            "or (:questionLike <> '%%' and lower(m.memoryKey) like lower(:questionLike)) " +
            "or lower(m.entityName) in :entities " +
            "order by m.usageCount desc, m.updatedAt desc")
    List<RagAgentMemory> findRelevant(@Param("intent") String intent,
                                      @Param("failureType") String failureType,
                                      @Param("questionLike") String questionLike,
                                      @Param("entities") List<String> entities,
                                      Pageable pageable);

    List<RagAgentMemory> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
