package com.example.rag.controller;

import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.dto.SearchResult;
import com.example.rag.service.DocumentChunkService;
import com.example.rag.service.DocumentService;
import com.example.rag.service.SearchService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class UploadController {

    private static final String UPLOAD_DIR = "D:/rag_api/uploads/";
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final SearchService searchService;

    public UploadController(DocumentService documentService,
                            DocumentChunkService documentChunkService,
                            SearchService searchService) {
        this.documentService = documentService;
        this.documentChunkService = documentChunkService;
        this.searchService = searchService;
    }

    @GetMapping("/test")
    public String test() {
        return "ok";
    }

    @GetMapping("/dbtest2")
    public String dbtest2() {
        try {
            Document document = documentService.saveDocument("manual.txt", "txt", "UPLOADED", "manual content");
            return String.valueOf(document.getId());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("dbtest2 save document failed", e);
            return "save document failed";
        }
    }

    @GetMapping("/documents/{documentId}/chunks")
    public List<DocumentChunk> getChunks(@PathVariable Long documentId) {
        return documentChunkService.getChunksByDocumentId(documentId);
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam("keyword") String keyword) {
        return searchService.searchByKeyword(keyword);
    }

    @PostMapping("/chunks/markEmbedded")
    public int markEmbedded(@RequestParam("documentId") Long documentId) {
        return documentChunkService.markEmbedded(documentId);
    }

    @GetMapping("/chunks/pending")
    public List<Map<String, Object>> getPendingChunks() {
        return documentChunkService.getPendingChunks();
    }

    @PostMapping("/rebuildChunks")
    public ResponseEntity<String> rebuildChunks(@RequestParam("documentId") Long documentId) {
        Optional<Document> optionalDocument = documentService.findById(documentId);
        if (!optionalDocument.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("document not found");
        }

        Document document = optionalDocument.get();
        String text = document.getContent();
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Document content is empty");
        }

        try {
            int chunkCount = documentChunkService.rebuildChunks(documentId, text);
            return ResponseEntity.ok("rebuild success, chunks = " + chunkCount);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("rebuild chunks failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("rebuild chunks failed");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("receive upload request");

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("file is empty");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        log.info("original file name = {}", originalFilename);
        if (!StringUtils.hasText(originalFilename)) {
            return ResponseEntity.badRequest().body("file name is invalid");
        }
        String lowerFileName = originalFilename.toLowerCase();
        if (!lowerFileName.endsWith(".txt") && !lowerFileName.endsWith(".pdf")) {
            return ResponseEntity.badRequest().body("only txt and pdf file are supported");
        }

        Path targetPath;
        try {
            log.info("start save local file");
            Path uploadPath = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadPath);

            targetPath = uploadPath.resolve(originalFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("local file saved path = {}", targetPath.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            log.error("save local file failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("save file failed");
        }

        String text;
        String fileType;
        try {
            log.info("start read document content");
            if (lowerFileName.endsWith(".txt")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
                fileType = "txt";
            } else {
                try (PDDocument pdf = PDDocument.load(file.getInputStream())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    text = stripper.getText(pdf);
                }
                fileType = "pdf";
            }
            log.info("text length = {}", text.length());
        } catch (IOException e) {
            e.printStackTrace();
            log.error("read document content failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("read file failed");
        }

        Document document;
        try {
            log.info("start save document metadata");
            document = documentService.saveDocument(originalFilename, fileType, "UPLOADED", text);
            log.info("document saved id = {}", document.getId());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("save document metadata failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("save document failed");
        }

        try {
            log.info("start save chunks");
            int chunkCount = documentChunkService.saveChunks(document.getId(), text);
            log.info("chunks saved count = {}", chunkCount);
            return ResponseEntity.ok("upload success");
        } catch (Exception e) {
            e.printStackTrace();
            log.error("save chunks failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("save chunks failed");
        }
    }
}
