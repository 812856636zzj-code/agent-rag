package com.example.rag.dto;

/** Maximum allowed regressions for a fixed-eval candidate. */
public record EvalGatePolicy(
        int maxGraphRegressions,
        int maxNewWrongAnswers,
        int maxCitationsLost,
        int maxNewAbstentions
) {
}
