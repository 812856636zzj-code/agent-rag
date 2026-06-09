# Concurrency And Rate Limit Design

## 1. Why Concurrency Control Is Needed

RAG and Agent systems need concurrency control because one user request can trigger multiple expensive operations:

- Multiple users may call `/ask` at the same time.
- A single `/ask` may trigger chunk search, entity search, relation search, SQL metadata search, and answer generation.
- Relation search, chunk search, and entity search may be parallelized later to reduce latency.
- Tool calls may be slow, fail, block, or hit external service limits.
- Without rate limits, database queries, future LLM calls, and tool services can be overloaded.
- Without timeouts and fallback, one slow component can block the full answer path.

The goal is not only to make answers correct, but also to make the system predictable under load.

## 2. Current Project Situation

The current project is still in demo and prototype stage. It already has multiple retrieval and answer paths:

- `/ask` supports baseline, graph, hybrid, and tool-agent style flows.
- `AgentRouter` or planner decides whether the request should use graph search, chunk search, SQL metadata search, or answer generation.
- `SearchService`, `RelationSearchService`, `ContextBuilderService`, and `AnswerBuilderService` are all part of the request chain.
- `AgentExecutor` can execute tools such as `GRAPH_SEARCH`, `CHUNK_SEARCH`, `SQL_SEARCH`, and `ANSWER_GENERATE`.
- `AgentExecutionTrace` records tool execution steps and is a good foundation for observability.

The project has not yet implemented a full thread pool strategy, rate limiter, circuit breaker, or isolated task queue. That is acceptable for the current demo, because the priority has been to validate retrieval quality and Agent tool routing first.

## 3. Main Concurrency Scenarios

The project may face these concurrency scenarios as it evolves:

- Multiple users call `/ask` concurrently.
- One question triggers chunk search, relation search, and entity search.
- Eval jobs repeatedly call baseline ask and graph ask.
- Multiple Agent tools run in sequence or in parallel.
- Trace and debug data are written while the main answer path is running.
- Memory update and answer generation may happen at the same time in future versions.

These scenarios should not share one uncontrolled execution path. Online question answering, offline evaluation, trace writing, and external tool calls need different stability policies.

## 4. Thread Pool Design

Future implementation should isolate major task categories with separate executors:

- `retrievalExecutor`: handles chunk search, entity search, relation search, and hybrid retrieval.
- `toolExecutor`: handles external tool calls and slower tool integrations.
- `evalExecutor`: handles batch eval jobs and offline comparison tasks.
- `traceExecutor`: handles asynchronous trace and debug persistence.

All tasks should not share one thread pool:

- A slow tool call should not block normal retrieval.
- Batch eval should not consume all capacity needed by online `/ask`.
- Trace writing failure should not fail the user-facing answer path.
- Different workloads need different queue sizes, timeout policies, and rejection behavior.

For the current project, the first practical step would be to isolate eval and external tools from online `/ask`.

## 5. Rate Limit Design

Rate limiting should be applied at several levels:

- User-level limit: controls how many requests one user can send per minute.
- Endpoint-level limit: applies different limits to `/ask`, `/eval`, and `/upload`.
- Tool-level limit: protects each external tool or internal expensive tool.
- Model-level limit: protects future LLM calls and avoids provider quota exhaustion.
- Database-level limit: limits concurrent SQL and metadata queries.

Example policy:

- `/ask`: 30 requests per user per minute.
- `/eval`: 3 requests per user per minute.
- `/upload`: 5 requests per user per minute.
- Tool call: 10 calls per tool per minute.

These numbers are design examples, not hard-coded constants. Real values should be based on database capacity, expected traffic, latency targets, and tool cost.

## 6. Timeout Design

Every task type should have an explicit timeout:

- Chunk search: 1-2 seconds.
- Relation search: 2-3 seconds.
- External tool call: 3-10 seconds.
- Answer generation: 10-30 seconds.
- Eval batch: controlled by task-level timeout.

Timeout handling should be explicit:

- Chunk search timeout: use available partial results or return no-answer.
- Relation search timeout: fall back to chunk-based answer.
- Tool timeout: mark the tool as unavailable and continue if possible.
- Answer generation timeout: return a controlled failure message.
- Trace writing timeout: ignore the trace failure for the main response and record it later if possible.

The timeout result should be recorded in `AgentExecutionTrace`, so later debugging can explain why the answer used a fallback path.

## 7. Retry Strategy

Retry should be limited to safe and useful cases.

Can retry:

- Idempotent queries.
- Temporary network failures.
- External tool `5xx` errors.
- Temporary database connection failures.

Should not retry:

- Invalid user input.
- Permission errors.
- Parameter validation failures.
- Non-idempotent writes.
- Retrieval results that clearly returned no matches.

Recommended retry policy:

- Retry at most 1-2 times.
- Use exponential backoff.
- Add jitter to avoid retry storms.
- Use a retry budget to cap total retry cost.
- Record retry count and final status in `AgentExecutionTrace`.

For the current project, metadata reads and relation/chunk searches are the safest retry candidates.

## 8. Circuit Breaker Design

Circuit breakers should protect the system when a dependency becomes unhealthy.

Possible breaker triggers:

- One external tool fails repeatedly.
- Relation search latency stays above threshold.
- Future LLM service failure rate is too high.
- Database connection pool is exhausted.

Fallback after breaker opens:

- Graph answer falls back to baseline answer.
- Relation-first retrieval falls back to chunk search.
- Tool-based answer falls back to normal RAG answer.
- Eval jobs pause or queue instead of competing with online traffic.

Circuit breaker state should be visible in metrics and trace. If a response is degraded because the breaker is open, `AgentExecutionTrace` should show that decision.

## 9. Backpressure And Queue Design

When load is too high, the system should apply backpressure instead of creating unlimited work:

- Do not create unlimited threads.
- Do not allow unbounded task queues.
- Use bounded queues for online, eval, tool, and trace workloads.
- When a queue is full, fail fast or return a controlled busy response.
- Eval and batch tasks should have lower priority than online `/ask`.

Recommended priority:

- Online `/ask` requests get the highest priority.
- Upload and rebuild tasks should be rate-limited because they can trigger chunking, extraction, and relation building.
- Eval tasks should be explicitly scheduled or throttled.
- Trace tasks should never block the main answer path.

## 10. Fallback Strategy

Fallback should be deterministic and explainable.

Recommended fallback priority:

- Full graph answer.
- Graph answer without relation path.
- Hybrid chunk answer.
- Baseline chunk answer.
- No answer with sources.
- Controlled failure response.

Examples:

- If `GRAPH_SEARCH` times out, continue with `CHUNK_SEARCH`.
- If `SQL_SEARCH` has no metadata, return the standard no-answer response.
- If answer generation fails, return evidence and a controlled failure message.
- If relation evidence is weak, lower confidence and include chunk evidence.

Every fallback decision should be recorded in `AgentExecutionTrace`, including the failed step, reason, latency, and selected fallback path.

## 11. Observability Metrics

The system should monitor both answer quality and runtime stability.

Core metrics:

- Request count.
- QPS.
- Latency p50 / p95 / p99.
- Timeout count.
- Retry count.
- Circuit breaker open count.
- Queue size.
- Thread pool active count.
- Tool success rate.
- Fallback rate.
- Error rate.

Agent-specific metrics:

- Route accuracy.
- Tool selection distribution.
- Graph hit rate.
- SQL fallback rate.
- Chunk fallback rate.
- No-answer rate.
- Average tool latency.
- Trace completeness rate.

These metrics are necessary because Agent systems can appear correct in small demos but become unstable under real traffic.

## 12. Mapping To Current Project

Future implementation can map concurrency and stability controls to the current project structure:

- `AgentRouter`: decides whether a request should take a high-cost path or a cheap baseline path.
- `SearchService`: can add retrieval timeout and database concurrency limits.
- `RelationSearchService`: can add graph search timeout, fallback, and circuit breaker.
- `AnswerBuilderService`: can add answer generation timeout and controlled failure handling.
- `EvalService`: should add batch concurrency limits and lower execution priority.
- `AgentExecutionTrace`: should record timeout, retry, fallback, and circuit breaker events.
- `AgentToolResult`: already has `success`, `errorMessage`, and `latencyMs`, so it is suitable for tool-level timeout and failure tracking.
- Future `ToolRegistry`: can centralize tool-level timeout, retry, permission, and rate limit policies.

The current project already has enough structure to add stability controls without rewriting the retrieval logic.

## 13. Interview Explanation

In an interview, this project can be explained this way:

The current project has not implemented full concurrency control and rate limiting yet because it is still focused on validating Graph-enhanced RAG, relation-first retrieval, SQL metadata fallback, and Agent tool tracing. Adding thread pools, rate limiters, circuit breakers, and isolation queues too early would increase complexity before the retrieval path is stable.

If this system were prepared for production, I would isolate online `/ask`, eval jobs, external tools, and trace writing with separate bounded executors. I would add user-level, endpoint-level, tool-level, model-level, and database-level rate limits. Each retrieval and tool step would have a timeout, safe retries would use exponential backoff with jitter, unhealthy dependencies would be protected by circuit breakers, and every fallback would be recorded in `AgentExecutionTrace`.

The key point is that an Agent system cannot be evaluated only by answer quality. It also needs stability, latency control, cost control, fallback behavior, traceability, and permission boundaries. Without those controls, a system that works in a demo can fail under real concurrent traffic.
