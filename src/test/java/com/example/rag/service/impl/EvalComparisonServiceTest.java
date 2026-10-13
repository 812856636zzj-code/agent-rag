package com.example.rag.service.impl;

import com.example.rag.dto.EvalCaseView;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalComparisonServiceTest {

    private final EvalComparisonService service = new EvalComparisonService();

    @Test
    void identifiesRegressedAnswerLostCitationAndNewAbstentionOnTheSameCase() {
        EvalRunResult base = result("EVAL-005", true, false, true, false);
        EvalRunResult candidate = result("EVAL-005", false, true, false, true);

        EvalComparison comparison = service.compare(
                run(1, "same-digest", 1), List.of(caseView(0, base)),
                run(2, "same-digest", 1), List.of(caseView(0, candidate)));

        assertEquals(1, comparison.caseCount());
        assertEquals(1, comparison.graphRegressions());
        assertEquals(1, comparison.newWrongAnswers());
        assertEquals(1, comparison.citationsLost());
        assertEquals(1, comparison.newAbstentions());
        assertTrue(comparison.cases().get(0).changes().contains("GRAPH_REGRESSED"));
        assertTrue(comparison.cases().get(0).changes().contains("CITATION_LOST"));
    }

    @Test
    void alignsCasesByStableIdEvenWhenCandidateOrderChanges() {
        EvalRunResult baseOne = result("one", false, true, false, true);
        EvalRunResult baseTwo = result("two", true, false, true, false);
        EvalRunResult candidateOne = result("one", true, false, true, false);
        EvalRunResult candidateTwo = result("two", true, false, true, false);

        EvalComparison comparison = service.compare(run(10, "same", 2),
                List.of(caseView(0, baseOne), caseView(1, baseTwo)),
                run(11, "same", 2),
                List.of(caseView(0, candidateTwo), caseView(1, candidateOne)));

        assertEquals(1, comparison.graphImprovements());
        assertEquals(1, comparison.fixedWrongAnswers());
        assertEquals(1, comparison.citationsRestored());
        assertEquals(1, comparison.recoveredAbstentions());
        assertEquals("one", comparison.cases().get(0).caseId());
        assertFalse(comparison.cases().get(1).graphRegressed());
    }

    @Test
    void rejectsDifferentQuestionSetsAndIncompleteSnapshots() {
        EvalCaseView item = caseView(0, result("one", true, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                run(1, "digest-a", 1), List.of(item),
                run(2, "digest-b", 1), List.of(item)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                run(1, "same", 1), List.of(item),
                run(2, "same", 1), List.of(caseView(0, result("other", true, false, true, false)))));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                run(1, "same", 2), List.of(item, item),
                run(2, "same", 2), List.of(item, item)));
    }

    private EvalRunResult result(String id, boolean graphHit, boolean wrongAnswer,
                                 boolean citationMatched, boolean abstained) {
        EvalRunResult result = new EvalRunResult();
        result.setId(id);
        result.setGraphHit(graphHit);
        result.setWrongAnswer(wrongAnswer);
        result.setCitationMatched(citationMatched);
        result.setAbstained(abstained);
        return result;
    }

    private EvalCaseView caseView(int position, EvalRunResult result) {
        return new EvalCaseView(result.getId(), position, result);
    }

    private EvalRunView run(long id, String digest, int count) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 10, 0);
        return new EvalRunView(id, "COMPLETED", digest, now, now, count,
                0, 0, 0, 0, 0, 0, 0, null);
    }
}
