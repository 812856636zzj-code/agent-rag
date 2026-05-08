package com.example.rag.repository;

import com.example.rag.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagEntityRepository extends JpaRepository<RagEntity, Long> {

    Optional<RagEntity> findByEntityTypeAndNormalizedName(String entityType, String normalizedName);
}
