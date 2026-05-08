package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document saveDocument(String fileName, String fileType, String status, String content) {
        Document document = new Document();
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setStatus(status);
        document.setContent(content);
        if (document.getUploadTime() == null) {
            document.setUploadTime(LocalDateTime.now());
        }
        return documentRepository.save(document);
    }

    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }
}
