package com.example.rag.service.impl;

import com.example.rag.dto.EvalRunQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Component
public class EvalQuestionSetLoader {

    private final ObjectMapper objectMapper;

    public EvalQuestionSetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvalQuestionSet load(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("load eval questions failed: " + path.toAbsolutePath(), ex);
        }
        List<EvalRunQuestion> questions;
        try {
            questions = objectMapper.readValue(bytes, new TypeReference<List<EvalRunQuestion>>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("eval questions are not a JSON array of cases", ex);
        }
        validate(questions);
        return new EvalQuestionSet(List.copyOf(questions), sha256(bytes));
    }

    private void validate(List<EvalRunQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("eval question set must contain at least one case");
        }
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < questions.size(); index++) {
            EvalRunQuestion question = questions.get(index);
            if (question == null || !StringUtils.hasText(question.getId())) {
                throw new IllegalArgumentException("eval case at position " + index + " has no ID");
            }
            if (!ids.add(question.getId())) {
                throw new IllegalArgumentException("duplicate eval case ID: " + question.getId());
            }
            if (!StringUtils.hasText(question.getQuestion())) {
                throw new IllegalArgumentException("eval case " + question.getId() + " has no question");
            }
            if (question.getExpectedKeywords() == null || question.getExpectedKeywords().isEmpty()) {
                throw new IllegalArgumentException("eval case " + question.getId() + " has no expected keywords");
            }
            for (String keyword : question.getExpectedKeywords()) {
                if (!StringUtils.hasText(keyword)) {
                    throw new IllegalArgumentException("eval case " + question.getId() + " has a blank expected keyword");
                }
            }
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
