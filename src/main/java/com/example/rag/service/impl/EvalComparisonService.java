package com.example.rag.service.impl;

import com.example.rag.dto.EvalCaseDelta;
import com.example.rag.dto.EvalCaseView;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalRunResult;
import com.example.rag.dto.EvalRunView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EvalComparisonService {

    public EvalComparison compare(EvalRunView baseRun, List<EvalCaseView> baseCases,
                                  EvalRunView candidateRun, List<EvalCaseView> candidateCases) {
        if (!"COMPLETED".equals(baseRun.status()) || !"COMPLETED".equals(candidateRun.status())) {
            throw new IllegalArgumentException("only completed eval runs can be compared");
        }
        if (!baseRun.questionSetSha256().equals(candidateRun.questionSetSha256())) {
            throw new IllegalArgumentException("eval runs use different question-set digests");
        }
        Map<String, EvalCaseView> candidateById = indexById(candidateCases);
        indexById(baseCases);
        if (baseCases.size() != candidateCases.size()) {
            throw new IllegalArgumentException("eval runs have different case counts");
        }

        List<EvalCaseDelta> deltas = new ArrayList<>();
        int graphImprovements = 0;
        int graphRegressions = 0;
        int newWrongAnswers = 0;
        int fixedWrongAnswers = 0;
        int citationsLost = 0;
        int citationsRestored = 0;
        int newAbstentions = 0;
        int recoveredAbstentions = 0;
        for (EvalCaseView baseCase : baseCases) {
            EvalCaseView candidateCase = candidateById.get(baseCase.caseId());
            if (candidateCase == null) {
                throw new IllegalArgumentException("candidate run is missing case " + baseCase.caseId());
            }
            EvalCaseDelta delta = compareCase(baseCase, candidateCase);
            deltas.add(delta);
            graphImprovements += delta.graphImproved() ? 1 : 0;
            graphRegressions += delta.graphRegressed() ? 1 : 0;
            newWrongAnswers += delta.newWrongAnswer() ? 1 : 0;
            fixedWrongAnswers += delta.wrongAnswerFixed() ? 1 : 0;
            citationsLost += delta.citationLost() ? 1 : 0;
            citationsRestored += delta.citationRestored() ? 1 : 0;
            newAbstentions += delta.newlyAbstained() ? 1 : 0;
            recoveredAbstentions += delta.abstentionRecovered() ? 1 : 0;
        }
        Set<String> baseIds = new HashSet<>();
        baseCases.forEach(item -> baseIds.add(item.caseId()));
        if (!baseIds.equals(candidateById.keySet())) {
            throw new IllegalArgumentException("candidate run has unexpected case IDs");
        }
        return new EvalComparison(
                baseRun.runId(), candidateRun.runId(), baseRun.questionSetSha256(), deltas.size(),
                graphImprovements, graphRegressions, newWrongAnswers, fixedWrongAnswers,
                citationsLost, citationsRestored, newAbstentions, recoveredAbstentions,
                List.copyOf(deltas));
    }

    private Map<String, EvalCaseView> indexById(List<EvalCaseView> cases) {
        Map<String, EvalCaseView> indexed = new HashMap<>();
        for (EvalCaseView item : cases) {
            if (item == null || item.caseId() == null || item.result() == null) {
                throw new IllegalArgumentException("eval run has an incomplete case snapshot");
            }
            if (indexed.putIfAbsent(item.caseId(), item) != null) {
                throw new IllegalArgumentException("duplicate case ID in eval run: " + item.caseId());
            }
        }
        return indexed;
    }

    private EvalCaseDelta compareCase(EvalCaseView baseCase, EvalCaseView candidateCase) {
        EvalRunResult base = baseCase.result();
        EvalRunResult candidate = candidateCase.result();
        boolean graphImproved = !base.isGraphHit() && candidate.isGraphHit();
        boolean graphRegressed = base.isGraphHit() && !candidate.isGraphHit();
        boolean newWrongAnswer = !base.isWrongAnswer() && candidate.isWrongAnswer();
        boolean wrongAnswerFixed = base.isWrongAnswer() && !candidate.isWrongAnswer();
        boolean citationLost = base.isCitationMatched() && !candidate.isCitationMatched();
        boolean citationRestored = !base.isCitationMatched() && candidate.isCitationMatched();
        boolean newlyAbstained = !base.isAbstained() && candidate.isAbstained();
        boolean abstentionRecovered = base.isAbstained() && !candidate.isAbstained();

        List<String> changes = new ArrayList<>();
        if (graphImproved) changes.add("GRAPH_IMPROVED");
        if (graphRegressed) changes.add("GRAPH_REGRESSED");
        if (newWrongAnswer) changes.add("NEW_WRONG_ANSWER");
        if (wrongAnswerFixed) changes.add("WRONG_ANSWER_FIXED");
        if (citationLost) changes.add("CITATION_LOST");
        if (citationRestored) changes.add("CITATION_RESTORED");
        if (newlyAbstained) changes.add("NEW_ABSTENTION");
        if (abstentionRecovered) changes.add("ABSTENTION_RECOVERED");
        return new EvalCaseDelta(baseCase.caseId(), baseCase.position(), List.copyOf(changes),
                graphImproved, graphRegressed, newWrongAnswer, wrongAnswerFixed,
                citationLost, citationRestored, newlyAbstained, abstentionRecovered,
                base, candidate);
    }
}
