package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

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

    public Optional<Document> findDuplicateDocument(String fileName, String content) {
        log.info("duplicate check start, fileName = {}", fileName);

        if (StringUtils.hasText(fileName)) {
            Optional<Document> sameFileNameDocument = documentRepository.findFirstByFileName(fileName);
            if (sameFileNameDocument.isPresent()) {
                log.info("duplicate document found by fileName, documentId = {}", sameFileNameDocument.get().getId());
                return sameFileNameDocument;
            }
        }

        Optional<Document> sameContentDocument = documentRepository.findDuplicateByContent(content);
        sameContentDocument.ifPresent(document ->
                log.info("duplicate document found by content, documentId = {}", document.getId()));
        return sameContentDocument;
    }
}
