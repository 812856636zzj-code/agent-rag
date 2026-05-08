package com.example.rag.repository;

import com.example.rag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, DocumentRepositoryCustom {

    Optional<Document> findFirstByFileName(String fileName);
}
