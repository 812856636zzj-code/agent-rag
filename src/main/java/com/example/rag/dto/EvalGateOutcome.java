package com.example.rag.dto;

import java.util.List;

/** A deterministic CI decision derived only from two stored completed runs. */
public record EvalGateOutcome(
        long baseRunId,
        long candidateRunId,
        String questionSetSha256,
        boolean passed,
        EvalGatePolicy policy,
        EvalComparison observed,
        List<EvalGateViolation> violations
) {
}
