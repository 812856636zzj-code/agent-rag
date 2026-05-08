package com.example.rag.service;

import com.example.rag.dto.EntityHit;
import com.example.rag.entity.DocumentChunk;

import java.util.List;

public interface EntityExtractionService {

    List<EntityHit> extractEntities(String text);

    void extractAndSave(Long documentId, List<DocumentChunk> chunks);

    void deleteLinksByDocumentId(Long documentId);
}
