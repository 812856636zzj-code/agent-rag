package com.example.rag.dto;

import java.time.LocalDateTime;

/** A stable, compact view of a persisted fixed-eval run. */
public record EvalRunView(
        long runId,
        String status,
        String questionSetSha256,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int total,
        int baselineHitCount,
        int graphHitCount,
        int sourceHitCount,
        int wrongAnswerCount,
        int citationMatchedCount,
        int citationMissingCount,
        int abstainedCount,
        String errorMessage
) {
}
