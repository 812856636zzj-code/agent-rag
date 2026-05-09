package com.example.rag.controller;

import com.example.rag.dto.RelationBuildResult;
import com.example.rag.dto.RelationQueryHit;
import com.example.rag.service.RelationBuilderService;
import com.example.rag.service.RelationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RelationController {

    private final RelationQueryService relationQueryService;
    private final RelationBuilderService relationBuilderService;

    public RelationController(RelationQueryService relationQueryService,
                              RelationBuilderService relationBuilderService) {
        this.relationQueryService = relationQueryService;
        this.relationBuilderService = relationBuilderService;
    }

    @GetMapping("/relations/by-entity")
    public List<RelationQueryHit> queryRelations(@RequestParam("entityName") String entityName) {
        return relationQueryService.queryRelations(entityName);
    }

    @PostMapping("/relations/rebuild/{documentId}")
    public RelationBuildResult rebuildRelations(@PathVariable("documentId") Long documentId) {
        return relationBuilderService.rebuildRelationsForDocument(documentId);
    }
}
