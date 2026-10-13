package com.example.rag.repository;

import com.example.rag.entity.EvalCaseRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalCaseRecordRepository extends JpaRepository<EvalCaseRecord, Long> {

    List<EvalCaseRecord> findByRunIdOrderByPositionAsc(Long runId, Pageable pageable);

    List<EvalCaseRecord> findByRunIdAndPositionGreaterThanOrderByPositionAsc(
            Long runId, Integer afterPosition, Pageable pageable);

    List<EvalCaseRecord> findByRunIdOrderByPositionAsc(Long runId);

    long countByRunId(Long runId);
}
