package com.example.rag.controller;

import com.example.rag.dto.RelationQueryHit;
import com.example.rag.service.RelationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RelationController {

    private final RelationQueryService relationQueryService;

    public RelationController(RelationQueryService relationQueryService) {
        this.relationQueryService = relationQueryService;
    }

    @GetMapping("/relations/by-entity")
    public List<RelationQueryHit> queryRelations(@RequestParam("entityName") String entityName) {
        return relationQueryService.queryRelations(entityName);
    }
}
