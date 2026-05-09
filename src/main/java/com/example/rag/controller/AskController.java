package com.example.rag.controller;

import com.example.rag.dto.AskRequest;
import com.example.rag.dto.GraphAskResponse;
import com.example.rag.service.AskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public ResponseEntity<GraphAskResponse> ask(@RequestBody AskRequest request) {
        log.info("receive ask request");

        String question = request == null ? null : request.getQuestion();
        log.info("question = {}", question);

        if (!StringUtils.hasText(question)) {
            GraphAskResponse response = new GraphAskResponse();
            response.setQuestion(question);
            response.setKeyword("");
            response.setAnswer("question must not be blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        long start = System.currentTimeMillis();
        GraphAskResponse response = askService.ask(question);
        long responseTime = System.currentTimeMillis() - start;

        log.info("keyword = {}", response.getKeyword());
        log.info("matched chunks = {}", response.getChunks().size());
        log.info("response time = {} ms", responseTime);
        return ResponseEntity.ok(response);
    }
}
