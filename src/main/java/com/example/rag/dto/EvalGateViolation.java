package com.example.rag.dto;

import java.util.List;

/** One exceeded budget with the affected fixed-case identifiers. */
public record EvalGateViolation(
        String metric,
        int observed,
        int allowed,
        List<String> caseIds
) {
}
