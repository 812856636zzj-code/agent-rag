package com.example.rag.dto;

/** Complete immutable case snapshot from a finished evaluation. */
public record EvalCaseView(String caseId, int position, EvalRunResult result) {
}
