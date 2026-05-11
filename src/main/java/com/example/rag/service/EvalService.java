package com.example.rag.service;

import com.example.rag.dto.Day6GraphEvalResult;
import com.example.rag.dto.Day6AgentToolEvalResult;
import com.example.rag.dto.Day7EvalResult;

import java.util.List;

public interface EvalService {

    List<Day6GraphEvalResult> runDay6GraphEval();

    List<Day6AgentToolEvalResult> runDay6AgentToolEval();

    List<Day7EvalResult> runDay7Eval();
}
