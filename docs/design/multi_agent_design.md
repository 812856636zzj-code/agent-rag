# Multi-Agent Design

## 1. Why Multi-Agent Is Needed

Complex RAG and Agent systems may need multiple agents because different parts of the task have different responsibilities and failure modes.

A single agent can handle simple routing and answering, but as the system grows, one agent can become overloaded with too many responsibilities:

- Understand the user question.
- Select retrieval strategy.
- Search chunks, entities, relations, and SQL metadata.
- Analyze graph paths.
- Execute tools safely.
- Generate grounded answers.
- Verify sources.
- Update memory.

Separating these responsibilities can reduce the complexity of a single prompt or planner, make the system easier to trace, and allow different permission boundaries for different tasks.

Multi-agent design is useful when:

- Retrieval, reasoning, tool execution, verification, and summarization need different policies.
- Some tools require stricter access control.
- Some tasks can run in parallel.
- Debugging requires knowing which role made which decision.
- Evaluation needs to measure routing, retrieval, reasoning, and generation separately.

## 2. Why This Project Has Not Implemented Full Multi-Agent Yet

This project has not implemented a full multi-agent architecture yet because the current demo does not require that level of complexity.

The current goal is to validate Graph-enhanced RAG and tool-based retrieval quality. For this stage, a single router plus multiple tools is enough to cover the main requirements:

- `GRAPH_SEARCH`
- `CHUNK_SEARCH`
- `SQL_SEARCH`
- `ANSWER_GENERATE`

Introducing full multi-agent orchestration too early would add costs:

- Agent scheduling complexity.
- State synchronization between agents.
- Conflict handling across agents.
- Higher latency.
- More debugging surfaces.
- More evaluation dimensions.
- More permission and safety boundaries.

The current priority is therefore:

- Make retrieval reliable.
- Make relation and SQL fallback explainable.
- Keep answer grounding visible.
- Build trace and evaluation before adding more agents.

## 3. Current Project Baseline

The current project is closer to:

Single Agent + Router + Tool-Calling Pattern

The main components are:

- `AskService`
- `AgentRouter`
- `AgentExecutionTrace`
- `AgentExecutionStep`
- `AgentToolResult`
- `SearchService`
- `RelationSearchService`
- `ContextBuilderService`
- `AnswerBuilderService`

Current behavior:

- `AgentRouter` decides which tools should be used.
- `AgentExecutor` runs the selected tools.
- `AgentExecutionTrace` records tool execution.
- `SearchService` handles chunk and hybrid retrieval.
- `RelationSearchService` handles relation search.
- `SqlSearchTool` handles safe Oracle metadata lookup.
- `AnswerBuilderService` and `AgentAnswerAssembler` generate answers from evidence.

This is not a full multi-agent system, but it already separates planning, tool execution, evidence, and trace.

The project currently chooses a single-agent tool executor because this is the smallest reliable architecture for the current problem scale:

- `AgentPlanner` decides the tool chain.
- `AgentExecutor` executes tools in a deterministic order.
- `AgentExecutionTrace` records each step.
- `AgentToolResult` keeps tool outputs structured.
- `AskResponse` exposes answer, sources, relation paths, and debug information.

This design avoids premature multi-agent complexity while still preserving a clear path to split planner, retriever, verifier, and memory responsibilities later.

## 4. Proposed Multi-Agent Roles

If the project evolves to a real multi-agent architecture, the first recommended split should be:

- `PlannerAgent`
- `RetrieverAgent`
- `ToolExecutorAgent`
- `VerifierAgent`
- `MemoryManagerAgent`

These five agents are enough for the next stage. More specialized agents should only be added when trace and eval show that one role has become too complex.

### PlannerAgent

Responsibilities:

- Classify question intent.
- Select route and tools.
- Decide whether graph, chunk, SQL metadata, or fallback is needed.
- Produce a structured plan instead of free-form instructions.

Current project mapping:

- `AgentPlanner`
- `RuleBasedAgentPlanner`
- `AgentRouteDecision`

### RetrieverAgent

Responsibilities:

- Run chunk search.
- Run entity search.
- Run relation search.
- Run hybrid retrieval.
- Merge and rank retrieval candidates.

Current project mapping:

- `SearchService`
- `RelationSearchService`
- `GraphSearchTool`
- `ChunkSearchTool`

### ToolExecutorAgent

Responsibilities:

- Execute tools.
- Validate tool parameters.
- Enforce permissions.
- Apply timeout and retry policies.
- Prevent unsafe SQL or external API calls.

Current project mapping:

- `AgentExecutor`
- `AgentTool`
- `SqlSearchTool`
- `AgentToolResult`

### VerifierAgent

Responsibilities:

- Check whether the answer cites sources.
- Check whether the answer is grounded.
- Check whether the question is answerable.
- Detect possible hallucination.
- Trigger fallback when evidence is insufficient.

Current project mapping:

- Existing eval hit rules.
- Future groundedness checker.

### MemoryManagerAgent

Responsibilities:

- Read short-term memory.
- Maintain working memory.
- Read and update long-term memory.
- Compress repeated traces or verified facts.

Current project mapping:

- `AgentExecutionTrace` as working-memory prototype.
- `memory_design.md` as future memory design.

The following roles are more detailed variants of the five-agent split and can be introduced later if needed.

### QueryUnderstandingAgent

Responsibilities:

- Rewrite ambiguous questions.
- Extract keywords.
- Identify intent.
- Identify entities.
- Normalize API paths, table names, fields, and service names.

Possible outputs:

- `normalizedQuestion`
- `intent`
- `entities`
- `queryTerms`
- `expectedEntityTypes`

### RetrievalAgent

Responsibilities:

- Run chunk search.
- Run entity search.
- Run relation search.
- Run hybrid retrieval.
- Merge and rank retrieval candidates.

Possible outputs:

- `chunkHits`
- `entityHits`
- `relationHits`
- `retrievalMode`

### GraphReasoningAgent

Responsibilities:

- Analyze relation paths.
- Prefer typed relations over weak co-occurrence.
- Select graph evidence.
- Resolve field-to-table, API-to-service, and service-to-class questions.

Possible outputs:

- `relationPaths`
- `graphEvidence`
- `graphConfidence`

### ToolExecutionAgent

Responsibilities:

- Execute tools.
- Validate tool parameters.
- Enforce permissions.
- Apply timeout and retry policies.
- Prevent unsafe SQL or external API calls.

Possible outputs:

- `toolResults`
- `toolErrors`
- `latencyMs`

### AnswerGenerationAgent

Responsibilities:

- Generate the final answer from evidence.
- Keep the answer grounded in sources.
- Format answer sections.
- Avoid using unsupported facts.

Possible outputs:

- `answer`
- `sources`
- `evidenceSummary`

### VerificationAgent

Responsibilities:

- Check whether the answer cites sources.
- Check whether the answer is grounded.
- Check whether the question is answerable.
- Detect possible hallucination.
- Trigger fallback when evidence is insufficient.

Possible outputs:

- `verified`
- `confidence`
- `verificationNotes`

### MemoryAgent

Responsibilities:

- Read short-term memory.
- Maintain working memory.
- Read and update long-term memory.
- Compress repeated traces or verified facts.

Possible outputs:

- `memorySnapshot`
- `memoryUpdates`
- `memoryConflicts`

## 5. Agent Collaboration Flow

A full multi-agent `/ask` request can follow this flow:

```text
User Question
-> QueryUnderstandingAgent
-> RetrievalAgent
-> GraphReasoningAgent
-> ToolExecutionAgent (optional)
-> AnswerGenerationAgent
-> VerificationAgent
-> MemoryAgent
-> Final Answer
```

Example:

```text
RAG_ENTITY_RELATIONS 表有哪些字段
-> QueryUnderstandingAgent identifies TABLE_FIELDS intent and target table
-> RetrievalAgent tries graph relation search
-> GraphReasoningAgent rejects unrelated API_USES_TABLE relation
-> ToolExecutionAgent runs SQL_SEARCH against USER_TAB_COLUMNS
-> AnswerGenerationAgent formats field list
-> VerificationAgent checks answer is grounded in SQL_METADATA source
-> MemoryAgent may store verified schema fact in future
-> Final Answer
```

## 6. State Sharing Design

Agents should not pass large natural-language context blocks without structure. They should exchange explicit structured state.

Minimum agent-to-agent message format:

- `taskId`
- `sender`
- `receiver`
- `intent`
- `inputSummary`
- `outputSummary`
- `evidence`
- `confidence`
- `traceId`

Example:

```json
{
  "taskId": "ask-20260511-001",
  "sender": "RetrieverAgent",
  "receiver": "VerifierAgent",
  "intent": "TABLE_FIELDS",
  "inputSummary": "Find fields of RAG_ENTITY_RELATIONS",
  "outputSummary": "SQL metadata returned 9 columns",
  "evidence": ["USER_TAB_COLUMNS"],
  "confidence": 0.95,
  "traceId": "trace-001"
}
```

Recommended shared state fields:

- `question`
- `normalizedQuestion`
- `intent`
- `entities`
- `routeDecision`
- `chunkHits`
- `relationHits`
- `relationPaths`
- `toolResults`
- `sources`
- `debug`
- `executionTrace`
- `memorySnapshot`

Structured state makes the system easier to inspect, test, and safely evolve.

### Avoiding Context Contamination

Multi-agent systems should avoid passing raw untrusted text as instruction.

Rules:

- Retrieved documents are evidence, not commands.
- Tool results are data, not follow-up instructions.
- Each agent receives only the fields it needs.
- Large raw chunks should be summarized into evidence snippets before crossing agent boundaries.
- System and developer instructions should never be copied into shared state.
- `debug`, `trace`, and internal tool parameters should be hidden unless the receiver is authorized.
- Suspicious text such as "ignore previous rules" should be marked as untrusted evidence.

For this project, `sources`, `relationPaths`, and `AgentToolResult.data` should remain structured. They should not be flattened into one large prompt-like context block.

### Permission Control

Multi-agent permission control should be backend-enforced.

Rules:

- `PlannerAgent` can propose tools, but cannot grant permission.
- `ToolExecutorAgent` must check a future `ToolRegistry` and permission whitelist before execution.
- High-risk tools require explicit approval or remain disabled.
- SQL tools must remain read-only unless separately reviewed.
- Memory updates require validation and scope checks.
- Each agent should have the minimum permissions needed for its role.

For this project, `SearchService` and `RelationSearchService` are low-risk read-only capabilities. `EvalService` is medium risk because it can consume resources. Future memory update tools should be treated as high risk.

### Failure Handling

Agent failures should be handled through deterministic fallback.

Examples:

- `PlannerAgent` fails: fall back to `CHUNK_SEARCH + ANSWER_GENERATE`.
- `RetrieverAgent` returns no relation: try chunk search or SQL metadata if applicable.
- `ToolExecutorAgent` times out: mark tool unavailable and continue with available evidence.
- `VerifierAgent` rejects answer: return no-answer or ask for more evidence.
- `MemoryManagerAgent` fails: skip memory update and do not block the answer.

All failures should be recorded in `AgentExecutionTrace` with agent name, failed step, error summary, latency, and fallback action.

In this project, the current equivalents are:

- `AgentRouteDecision`
- `AgentToolInput`
- `AgentToolResult`
- `AgentExecutionTrace`
- `AskResponse.sources`
- `AskResponse.relationPaths`
- `AskResponse.debug`

## 7. Conflict Handling

Multi-agent systems can produce conflicting outputs.

Examples:

- `RetrievalAgent` finds a chunk saying one field list.
- `GraphReasoningAgent` finds a different relation path.
- `ToolExecutionAgent` returns SQL metadata that disagrees with extracted text.
- `VerificationAgent` decides evidence is insufficient.
- `MemoryAgent` has old facts that conflict with current evidence.

Conflict handling strategy:

- Prefer current source evidence over stale memory.
- Prefer verified facts over weak extracted facts.
- Prefer SQL metadata for table schema questions.
- Prefer typed relations over weak `MENTIONED_WITH` or `RELATED_TO`.
- Downgrade to chunk-based answer when graph evidence is weak.
- Mark low confidence when evidence conflicts.
- Record conflict reason in trace.

For this project:

- `SQL_SEARCH` should outrank weak graph relation for schema fallback.
- `TABLE_HAS_FIELD` should outrank unrelated `API_USES_TABLE` for field-list questions.
- `CHUNK_SEARCH` should remain fallback when graph and SQL miss.

## 8. Scheduling Strategy

Multi-agent scheduling can be designed in several ways.

### Sequential Pipeline

Agents run in fixed order.

Pros:

- Simple.
- Easy to debug.

Cons:

- Less flexible.
- Can waste time on unnecessary steps.

### Router-Based Dynamic Dispatch

A router or planner chooses the next agents or tools.

Pros:

- Fits this project.
- Easy to evaluate.
- Keeps flow deterministic.

Cons:

- Rule quality matters.

### Parallel Retrieval + Merge

Different retrieval agents run in parallel, then merge results.

Pros:

- Better recall.
- Lower latency when tools are independent.

Cons:

- Merge and ranking become harder.

### Supervisor Agent Pattern

A supervisor coordinates multiple specialist agents.

Pros:

- Flexible.
- Useful for complex tasks.

Cons:

- Harder to test.
- More expensive.
- More failure modes.

Current recommendation:

Router-Based Dynamic Dispatch + Partial Parallel Retrieval

This matches the current architecture and avoids premature complexity.

## 9. Observability And Trace

Multi-agent systems require strong observability. Without trace, failures become difficult to assign and debug.

Trace should record:

- Each agent input.
- Each agent output.
- Latency.
- Tool usage.
- Matched sources.
- Failure reason.
- Fallback path.

Current project mapping:

- `AgentExecutionTrace` can become the top-level trace.
- `AgentExecutionStep` can represent tool or agent steps.
- `AskResponse.debug` already exposes route, tools, and execution trace.

Future extension:

- Add `agentName` to each step.
- Add confidence and conflict markers.
- Add trace persistence for evaluation and debugging.

## 10. Evaluation Design

Multi-agent evaluation cannot only check whether the final answer looks good. It should measure each layer.

Evaluation dimensions:

- Route accuracy.
- Retrieval hit rate.
- Relation path correctness.
- Answer groundedness.
- Hallucination rate.
- Tool success rate.
- Fallback rate.
- Latency.
- Token cost.

For this project, the current eval framework already supports part of this:

- `router-only` vs `tool-agent`
- `graphHit`
- `sqlSearchHit`
- `chunkSearchHit`
- `traceHit`
- `sourceHit`
- `evidenceHit`
- `expectedToolsHit`

Future multi-agent eval can add:

- Agent-level success rate.
- Conflict resolution accuracy.
- Verification pass rate.
- Memory retrieval quality.

## 11. Risks Of Over-Engineering

Multi-agent architecture can make a system worse if introduced too early.

Risks:

- More moving parts.
- More debugging cost.
- Higher latency.
- Higher prompt cost if LLM agents are used.
- Agents may disagree without a clear resolution policy.
- Small tasks can become slower and less reliable.
- Teams may confuse "more agents" with better reasoning.

For this project, the right path is incremental:

- First build reliable retrieval.
- Then add tools.
- Then add trace.
- Then add eval.
- Only then split into true agents when the task complexity justifies it.

## 12. Mapping To Current Project

The current project is not a full multi-agent system, but it has the foundation for one.

Mapping:

- `AgentRouter` can evolve into a Supervisor or Scheduler.
- `SearchService` can evolve into `RetrievalAgent`.
- `RelationSearchService` can evolve into `GraphReasoningAgent`.
- `ContextBuilderService` can become evidence assembly.
- `AnswerBuilderService` can evolve into `AnswerGenerationAgent`.
- `AgentExecutionTrace` can become the multi-agent trace foundation.
- `memory_design.md` can become the design base for `MemoryAgent`.

Current architecture already supports:

- Structured routing.
- Tool execution.
- SQL metadata fallback.
- Execution trace.
- Eval comparison.

That makes multi-agent evolution additive rather than a rewrite.

## 13. Interview Explanation

In an interview, this project can be explained as follows:

> I did not force a full multi-agent design in the early stage because the demo task did not require it. The priority was to prove that Graph-enhanced RAG, SQL metadata fallback, evidence selection, and execution trace can improve answer quality over plain chunk retrieval.
>
> The current architecture is a single Agent with a router and tool-calling pattern. It already separates planning, tool execution, answer assembly, and trace. That means it can evolve naturally into a multi-agent system later.
>
> The hard part of multi-agent systems is not simply creating multiple prompts. The hard part is scheduling, shared state, conflict handling, traceability, evaluation, permissions, and latency control. This project keeps those boundaries explicit first, then leaves room to split responsibilities into specialized agents when the complexity actually requires it.
