package com.example.rag.repository;

import com.example.rag.entity.Document;

import java.util.Optional;

public interface DocumentRepositoryCustom {

    Optional<Document> findDuplicateByContent(String content);
}
