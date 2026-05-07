package com.example.rag.repository;

import com.example.rag.entity.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
}
