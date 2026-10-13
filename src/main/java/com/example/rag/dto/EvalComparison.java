package com.example.rag.dto;

import java.util.List;

/** A comparison of two persisted runs using the same question-set digest. */
public record EvalComparison(
        long baseRunId,
        long candidateRunId,
        String questionSetSha256,
        int caseCount,
        int graphImprovements,
        int graphRegressions,
        int newWrongAnswers,
        int fixedWrongAnswers,
        int citationsLost,
        int citationsRestored,
        int newAbstentions,
        int recoveredAbstentions,
        List<EvalCaseDelta> cases
) {
}
