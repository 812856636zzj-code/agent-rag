package com.example.rag.dto;

import java.util.List;

/** Directional changes for one case, from base to candidate. */
public record EvalCaseDelta(
        String caseId,
        int position,
        List<String> changes,
        boolean graphImproved,
        boolean graphRegressed,
        boolean newWrongAnswer,
        boolean wrongAnswerFixed,
        boolean citationLost,
        boolean citationRestored,
        boolean newlyAbstained,
        boolean abstentionRecovered,
        EvalRunResult base,
        EvalRunResult candidate
) {
}
