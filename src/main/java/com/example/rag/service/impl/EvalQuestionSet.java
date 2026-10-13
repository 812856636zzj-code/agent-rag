package com.example.rag.service.impl;

import com.example.rag.dto.EvalRunQuestion;

import java.util.List;

/** Parsed cases and digest of the exact UTF-8 bytes used for this run. */
public record EvalQuestionSet(List<EvalRunQuestion> questions, String sha256) {
}
