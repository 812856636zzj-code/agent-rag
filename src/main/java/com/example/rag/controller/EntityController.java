package com.example.rag.controller;

import com.example.rag.service.EntityQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class EntityController {

    private final EntityQueryService entityQueryService;

    public EntityController(EntityQueryService entityQueryService) {
        this.entityQueryService = entityQueryService;
    }

    @GetMapping("/entities/recent")
    public List<Map<String, Object>> getRecentEntities(@RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return entityQueryService.getRecentEntities(limit);
    }

    @GetMapping("/entities/by-document/{documentId}")
    public List<Map<String, Object>> getEntitiesByDocumentId(@PathVariable Long documentId,
                                                             @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return entityQueryService.getEntitiesByDocumentId(documentId, limit);
    }
}
