package com.example.rag.repository;

import com.example.rag.entity.EvalRunRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalRunRecordRepository extends JpaRepository<EvalRunRecord, Long> {

    List<EvalRunRecord> findAllByOrderByIdDesc(Pageable pageable);

    List<EvalRunRecord> findByIdLessThanOrderByIdDesc(Long beforeId, Pageable pageable);
}
