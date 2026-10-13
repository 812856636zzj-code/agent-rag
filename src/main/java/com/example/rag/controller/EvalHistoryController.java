package com.example.rag.controller;

import com.example.rag.dto.EvalCasePage;
import com.example.rag.dto.EvalComparison;
import com.example.rag.dto.EvalRunPage;
import com.example.rag.dto.EvalRunView;
import com.example.rag.dto.EvalGateOutcome;
import com.example.rag.dto.EvalGatePolicy;
import com.example.rag.service.impl.EvalHistoryService;
import com.example.rag.service.impl.EvalRegressionGateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvalHistoryController {

    private final EvalHistoryService historyService;
    private final EvalRegressionGateService gateService;

    public EvalHistoryController(EvalHistoryService historyService,
                                 EvalRegressionGateService gateService) {
        this.historyService = historyService;
        this.gateService = gateService;
    }

    @GetMapping("/eval/runs")
    public EvalRunPage list(@RequestParam(defaultValue = "20") int limit,
                            @RequestParam(required = false) Long before) {
        return historyService.list(limit, before);
    }

    @GetMapping("/eval/runs/{runId}")
    public EvalRunView get(@PathVariable long runId) {
        return historyService.get(runId);
    }

    @GetMapping("/eval/runs/{runId}/cases")
    public EvalCasePage cases(@PathVariable long runId,
                              @RequestParam(defaultValue = "20") int limit,
                              @RequestParam(required = false) Integer after) {
        return historyService.cases(runId, limit, after);
    }

    @GetMapping("/eval/compare")
    public EvalComparison compare(@RequestParam("base") long baseRunId,
                                  @RequestParam("candidate") long candidateRunId) {
        return historyService.compare(baseRunId, candidateRunId);
    }

    @GetMapping("/eval/gate")
    public EvalGateOutcome gate(@RequestParam("base") long baseRunId,
                                @RequestParam("candidate") long candidateRunId,
                                @RequestParam(defaultValue = "0") int maxGraphRegressions,
                                @RequestParam(defaultValue = "0") int maxNewWrongAnswers,
                                @RequestParam(defaultValue = "0") int maxCitationsLost,
                                @RequestParam(defaultValue = "0") int maxNewAbstentions) {
        return gateService.evaluate(baseRunId, candidateRunId,
                new EvalGatePolicy(maxGraphRegressions, maxNewWrongAnswers,
                        maxCitationsLost, maxNewAbstentions));
    }
}
