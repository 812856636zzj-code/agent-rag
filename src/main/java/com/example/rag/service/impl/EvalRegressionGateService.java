package com.example.rag.service.impl;

import com.example.rag.dto.EvalCaseDelta;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalGateOutcome;
import com.example.rag.dto.EvalGatePolicy;
import com.example.rag.dto.EvalGateViolation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Service
public class EvalRegressionGateService {

    private final EvalHistoryService historyService;

    public EvalRegressionGateService(EvalHistoryService historyService) {
        this.historyService = historyService;
    }

    public EvalGateOutcome evaluate(long baseRunId, long candidateRunId, EvalGatePolicy policy) {
        validate(policy);
        return assess(historyService.compare(baseRunId, candidateRunId), policy);
    }

    /** Evaluate a stored comparison without calling the answering pipeline again. */
    EvalGateOutcome assess(EvalComparison comparison, EvalGatePolicy policy) {
        validate(policy);
        List<EvalGateViolation> violations = new ArrayList<>();
        addViolation(violations, "graphRegressions", comparison.graphRegressions(),
                policy.maxGraphRegressions(), comparison.cases(), EvalCaseDelta::graphRegressed);
        addViolation(violations, "newWrongAnswers", comparison.newWrongAnswers(),
                policy.maxNewWrongAnswers(), comparison.cases(), EvalCaseDelta::newWrongAnswer);
        addViolation(violations, "citationsLost", comparison.citationsLost(),
                policy.maxCitationsLost(), comparison.cases(), EvalCaseDelta::citationLost);
        addViolation(violations, "newAbstentions", comparison.newAbstentions(),
                policy.maxNewAbstentions(), comparison.cases(), EvalCaseDelta::newlyAbstained);
        return new EvalGateOutcome(comparison.baseRunId(), comparison.candidateRunId(),
                comparison.questionSetSha256(), violations.isEmpty(), policy, comparison,
                List.copyOf(violations));
    }

    private void addViolation(List<EvalGateViolation> violations, String metric, int observed,
                              int allowed, List<EvalCaseDelta> cases,
                              Predicate<EvalCaseDelta> affected) {
        if (observed <= allowed) {
            return;
        }
        List<String> ids = cases.stream().filter(affected).map(EvalCaseDelta::caseId).toList();
        violations.add(new EvalGateViolation(metric, observed, allowed, ids));
    }

    private void validate(EvalGatePolicy policy) {
        if (policy == null || policy.maxGraphRegressions() < 0
                || policy.maxNewWrongAnswers() < 0 || policy.maxCitationsLost() < 0
                || policy.maxNewAbstentions() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "regression budgets must be nonnegative");
        }
    }
}
