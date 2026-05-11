package com.example.rag.controller;

import com.example.rag.dto.Day6GraphEvalResult;
import com.example.rag.dto.Day6AgentToolEvalResult;
import com.example.rag.dto.Day7EvalResult;
import com.example.rag.service.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/eval/day6/graph/run")
    public List<Day6GraphEvalResult> runDay6GraphEval() {
        return evalService.runDay6GraphEval();
    }

    @GetMapping("/eval/day7/run")
    public List<Day7EvalResult> runDay7Eval() {
        return evalService.runDay7Eval();
    }

    @GetMapping("/eval/day6/agent-tool/run")
    public List<Day6AgentToolEvalResult> runDay6AgentToolEval() {
        return evalService.runDay6AgentToolEval();
    }
}
