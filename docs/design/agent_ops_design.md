# Agent Ops Design

## 1. Current Request Flow

Current `/ask` flow is a single-agent tool workflow:

```text
User
-> AskController
-> AskService
-> AgentPlanner / AgentRouter
-> AgentExecutor
-> AgentTool
   -> GRAPH_SEARCH
   -> CHUNK_SEARCH
   -> SQL_SEARCH
   -> ANSWER_GENERATE
-> AgentExecutionTrace
-> AskResponse
```

The main response fields used for ops and debugging are:

- `mode`
- `retrievalMode`
- `sources`
- `relationPaths`
- `debug.routeDecision`
- `debug.executedTools`
- `debug.executionTrace.steps`

This request path is deterministic enough for debugging, but it still needs operational controls before production traffic.

## 2. Concurrency Risk Points

Concurrency risks:

- Many users call `/ask` at the same time.
- One `/ask` may trigger `GRAPH_SEARCH`, `CHUNK_SEARCH`, `SQL_SEARCH`, and `ANSWER_GENERATE`.
- Eval jobs may repeatedly call baseline and graph ask.
- SQL metadata queries may hit Oracle concurrently.
- Relation search can become expensive when entities are broad.
- Trace and debug data may become large.
- Tool failures can block the whole answer path if timeout is missing.

Risk summary:

- Database overload is the first likely bottleneck.
- Eval traffic can affect online `/ask` if not isolated.
- SQL metadata fallback needs independent protection.
- Trace payload size can grow quickly for badcases.

## 3. Tool Call Cost Analysis

Different tools have different cost profiles.

### GRAPH_SEARCH

- Cost source: relation table joins and entity matching.
- Risk: broad entity terms may return many relation candidates.
- Protection: topK, timeout, relation type filtering, query limit.

### CHUNK_SEARCH

- Cost source: keyword search and CLOB/content matching.
- Risk: large document chunks and fuzzy matching can be slow.
- Protection: result limit, document type filtering, timeout.

### SQL_SEARCH

- Cost source: Oracle metadata query against `USER_TAB_COLUMNS`.
- Risk: high-frequency schema questions can still create DB pressure.
- Protection: strict parameter validation, per-tool rate limit, cacheable result.

### ANSWER_GENERATE

- Cost source: evidence assembly and formatting.
- Risk: future LLM generation will be much more expensive.
- Protection: max evidence size, timeout, future model-level rate limit.

## 4. Rate Limit Design

Rate limiting should be enforced before expensive tool execution.

### Single-Node Token Bucket

Use case:

- Simple local deployment.
- Demo or low-traffic environment.
- No cross-node consistency requirement.

Design:

- Each key has a token bucket.
- Tokens refill at a fixed rate.
- A request consumes one or more tokens.
- If no token is available, return `429 Too Many Requests`.

Pros:

- Simple.
- Fast.
- No external dependency.

Cons:

- Not accurate across multiple app instances.
- Restart clears state.

### Redis Distributed Token Bucket

Use case:

- Multi-instance deployment.
- Need shared rate limit state.

Design:

- Store token count and last refill timestamp in Redis.
- Use Lua script or atomic operation to avoid race conditions.
- Key format can include user, endpoint, and tool.

Example keys:

- `rate:user:{userId}:ask`
- `rate:ip:{ip}:ask`
- `rate:tool:{toolType}`
- `rate:apikey:{apiKey}`

Pros:

- Works across nodes.
- Efficient.
- Good for QPS-style limits.

Cons:

- Requires Redis.
- Needs careful clock and atomicity handling.

### Redis ZSET Sliding Window

Use case:

- Need more accurate rolling-window limits.
- Need auditability of recent request timestamps.

Design:

- Store request timestamps in a Redis ZSET.
- Remove entries older than the window.
- Count remaining entries.
- Reject if count exceeds threshold.

Pros:

- Accurate sliding window.
- Easy to inspect recent request patterns.

Cons:

- More Redis memory and write cost.
- Needs cleanup.

## 5. Rate Limit Dimensions

Recommended dimensions:

- `userId`: prevents one user from exhausting shared capacity.
- `ip`: protects anonymous or unauthenticated endpoints.
- `apiKey`: controls application-level quota.
- `toolType`: protects expensive tools independently.

Example policies:

- `/ask` per user: medium quota.
- `/eval` per user: low quota.
- `SQL_SEARCH` per user and per tool: strict quota.
- `ANSWER_GENERATE` future LLM tool: model-level quota.

Rate limit decisions should be recorded in trace when a request is degraded or rejected.

## 6. SQL_SEARCH Rate Limit

`SQL_SEARCH` needs separate protection because it directly touches Oracle metadata.

Policy:

- Allow only validated table names.
- Only query `USER_TAB_COLUMNS`.
- No user-provided SQL execution.
- Apply tool-level rate limit by `toolType = SQL_SEARCH`.
- Apply user-level or API key-level limit for schema lookup questions.
- Prefer cache for repeated table metadata queries.

Example:

- `SQL_SEARCH` per user: 10 calls per minute.
- `SQL_SEARCH` global: capped by database capacity.
- Unknown table repeated misses: short negative cache to avoid repeated DB hits.

Failure behavior:

- If SQL rate limit is exceeded, fall back to graph or chunk result if available.
- If no safe fallback exists, return controlled no-answer or busy response.

## 7. Timeout And Retry Strategy

Timeouts should be set per tool:

- `GRAPH_SEARCH`: 2-3 seconds.
- `CHUNK_SEARCH`: 1-2 seconds.
- `SQL_SEARCH`: 1 second for metadata query.
- `ANSWER_GENERATE`: 3-5 seconds for rule-based generation, longer only for future LLM.
- Eval batch: task-level timeout.

Retry rules:

- Retry only idempotent read operations.
- Retry temporary DB connection failures.
- Retry external tool `5xx` in future integrations.
- Do not retry validation failures.
- Do not retry permission failures.
- Do not retry known no-result cases.

Retry policy:

- Max 1-2 retries.
- Exponential backoff.
- Jitter.
- Retry budget per request.

All timeout and retry events should be added to `AgentExecutionTrace`.

## 8. Trace Dashboard Design

A trace dashboard should make Agent badcases easy to inspect.

Dashboard fields:

- `mode`
- `retrievalMode`
- `routeDecision`
- `executedTools`
- `executionTrace.steps`
- `latencyMs`
- `errorMessage`
- `sources`
- `relationPaths`
- `fallbackAction`

Step-level display:

- Step index.
- Tool type.
- Input summary.
- Output summary.
- Success flag.
- Latency.
- Error message.

Useful filters:

- `mode = BASELINE_FALLBACK`
- `toolType = SQL_SEARCH`
- `success = false`
- `latencyMs > threshold`
- `errorMessage is not empty`
- no-answer responses
- graph miss followed by SQL fallback

The current `AgentExecutionTrace` and `AgentExecutionStep` already provide the core data shape for this dashboard.

## 9. Badcase Debugging Flow

Recommended badcase flow:

1. Check final `answer` and `mode`.
2. Check `routeDecision.selectedTools`.
3. Check `debug.executedTools`.
4. Inspect `executionTrace.steps` in order.
5. Identify whether the failure is route, retrieval, tool execution, evidence, or answer assembly.
6. Check `sources` and `relationPaths`.
7. Compare expected tool chain with actual tool chain.
8. Check timeout, error, and fallback markers.
9. Reproduce with the same question.
10. Add or update eval case if the badcase is important.

Common examples:

- Wrong route: classifier or planner issue.
- Graph miss: relation search or relation build issue.
- SQL fallback not executed: AgentExecutor graph-hit validation issue.
- Bad evidence: evidence selector or source ranking issue.
- Empty answer: answer assembler fallback issue.
- Slow request: tool timeout or database query issue.

## 10. Production Hardening Suggestions

Recommended next steps before production:

- Add bounded thread pools for online ask, eval, tools, and trace.
- Add per-user, per-IP, per-apiKey, and per-tool rate limits.
- Add SQL metadata cache and negative cache.
- Add circuit breaker for database and external tools.
- Persist trace for badcases and sampled successful requests.
- Add trace dashboard.
- Add alerting for timeout, fallback, and error rate.
- Add source and evidence size limits.
- Add permission whitelist for tools.
- Separate eval traffic from online traffic.
- Add load tests for 100 concurrent users.

## 11. Interview Explanation

If asked whether 100 people using the system at the same time will make it slow, the answer is:

The current demo does not yet implement full production concurrency control, but the architecture already exposes the right boundaries. `/ask` goes through planner, executor, tools, trace, and answer response, so we can add rate limits and timeouts at each boundary without rewriting retrieval logic.

For production, I would add per-user, per-IP, per-apiKey, and per-tool rate limits. Single-node deployments can use an in-memory token bucket. Multi-node deployments should use Redis token bucket or Redis ZSET sliding window. `SQL_SEARCH` needs its own stricter limit because it queries Oracle metadata.

For badcase debugging, I would use a trace dashboard showing `mode`, `routeDecision`, `executedTools`, `executionTrace.steps`, `latencyMs`, and `errorMessage`. That lets us quickly tell whether a failure came from routing, retrieval, SQL fallback, tool timeout, or answer assembly. Agent systems need this operational layer because answer quality alone is not enough; the system must also be stable, explainable, and controllable under concurrent traffic.
