package com.example.rag.service.impl;

import com.example.rag.dto.EvalCaseDelta;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalGateOutcome;
import com.example.rag.dto.EvalGatePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalRegressionGateServiceTest {

    private final EvalRegressionGateService service = new EvalRegressionGateService(null);

    @Test
    void strictPolicyFailsWithEachRegressedCaseId() {
        EvalGateOutcome outcome = service.assess(regressed(), new EvalGatePolicy(0, 0, 0, 0));

        assertFalse(outcome.passed());
        assertEquals(4, outcome.violations().size());
        assertEquals("graphRegressions", outcome.violations().get(0).metric());
        assertEquals(List.of("one"), outcome.violations().get(0).caseIds());
        assertEquals("newWrongAnswers", outcome.violations().get(1).metric());
        assertEquals(List.of("one", "two"), outcome.violations().get(1).caseIds());
        assertEquals(2, outcome.violations().get(1).observed());
        assertEquals("citationsLost", outcome.violations().get(2).metric());
        assertEquals("newAbstentions", outcome.violations().get(3).metric());
        assertEquals(12, outcome.baseRunId());
        assertEquals(13, outcome.candidateRunId());
    }

    @Test
    void explicitBudgetsPermitKnownRegressionsWithoutChangingObservedCounts() {
        EvalGateOutcome outcome = service.assess(regressed(), new EvalGatePolicy(1, 2, 1, 1));

        assertTrue(outcome.passed());
        assertTrue(outcome.violations().isEmpty());
        assertEquals(1, outcome.observed().graphRegressions());
        assertEquals(2, outcome.observed().newWrongAnswers());
    }

    @Test
    void onlyExceededBudgetsAreReported() {
        EvalGateOutcome outcome = service.assess(regressed(), new EvalGatePolicy(1, 1, 1, 1));

        assertFalse(outcome.passed());
        assertEquals(1, outcome.violations().size());
        assertEquals("newWrongAnswers", outcome.violations().get(0).metric());
        assertEquals(1, outcome.violations().get(0).allowed());
    }

    @Test
    void negativeBudgetsAreRejectedAsClientErrors() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assess(regressed(), new EvalGatePolicy(0, -1, 0, 0)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private EvalComparison regressed() {
        EvalCaseDelta first = new EvalCaseDelta("one", 0,
                List.of("GRAPH_REGRESSED", "NEW_WRONG_ANSWER", "CITATION_LOST", "NEW_ABSTENTION"),
                false, true, true, false, true, false, true, false, null, null);
        EvalCaseDelta second = new EvalCaseDelta("two", 1,
                List.of("NEW_WRONG_ANSWER"),
                false, false, true, false, false, false, false, false, null, null);
        return new EvalComparison(12, 13, "same-digest", 2,
                0, 1, 2, 0, 1, 0, 1, 0, List.of(first, second));
    }
}
