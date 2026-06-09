# Agent Memory Design

## 1. Why Agent Needs Memory

Agent memory is used to preserve task context across tool calls, turns, sessions, and repeated user goals. Without memory, an agent can only react to the current input and the immediate retrieval result. That makes multi-step reasoning fragile, because the agent cannot reliably track what it has already tried, which tools were used, which evidence was trusted, or which user preferences should affect future behavior.

In a RAG system, memory is useful for four reasons:

- Preserve execution state across tools.
- Avoid repeating failed retrieval paths.
- Reuse validated facts and user preferences.
- Make answers explainable through traceable intermediate state.

For this project, memory is especially relevant because the system already uses multiple tools:

- `GRAPH_SEARCH`
- `CHUNK_SEARCH`
- `SQL_SEARCH`
- `ANSWER_GENERATE`

The agent needs a place to keep the intermediate results from these tools and decide which result should dominate the final answer.

## 2. Why This Project Has Not Implemented Full Memory Yet

This project is currently focused on building a reliable tool-based Agentic RAG prototype. The priority is to make retrieval paths deterministic, auditable, and easy to evaluate before introducing persistent memory.

Full memory is intentionally postponed because it would introduce additional complexity:

- Session identity and lifecycle management.
- Data retention policy.
- Memory conflict handling.
- Privacy and permission boundaries.
- Memory evaluation and regression testing.
- Storage schema and cleanup strategy.

The current system already has the first useful building blocks:

- `executionTrace` records the tool execution process.
- `routeDecision` records the planned tool chain.
- `toolResults` can represent intermediate tool outputs.
- `sources`, `relationPaths`, and `debug` expose evidence and execution state.

These are closer to working memory than long-term memory. They are enough for explainability and debugging, but not yet enough for persistent personalization or cross-session learning.

## 3. Memory Layering

### 3.1 Short-Term Memory

Short-term memory stores state within a single user turn or a short multi-turn interaction.

Examples:

- Current question.
- Current classified question type.
- Current selected tools.
- Current retrieved chunks.
- Current relation hits.
- Current SQL metadata result.
- Current answer draft.

Storage options:

- In-memory request object.
- `AgentToolInput`.
- `AgentToolResult`.
- `AskResponse.debug`.

In the current project, short-term memory mostly lives in:

- `AgentToolInput`
- `AgentToolResult`
- `AskResponse`

### 3.2 Working Memory

Working memory stores the active reasoning and execution state of the agent during one task.

Examples:

- Tool execution order.
- Tool latency.
- Tool success or failure.
- Intermediate summaries.
- Which retrieval path was selected.
- Which evidence was used.
- Whether fallback was triggered.

Storage options:

- `AgentExecutionTrace`
- `AgentExecutionStep`
- Runtime-only task context object.

In the current project, `executionTrace` is already the working memory prototype. It records:

- `traceId`
- `steps`
- `stepIndex`
- `toolType`
- `success`
- `inputSummary`
- `outputSummary`
- `errorMessage`
- `latencyMs`

This is enough to explain why an answer used graph search, SQL metadata, chunk search, or fallback.

### 3.3 Long-Term Memory

Long-term memory stores reusable information across sessions or users.

Examples:

- User preferences.
- Stable domain facts confirmed by repeated use.
- Frequently asked questions.
- Historical failed retrieval patterns.
- Trusted mappings such as field-to-table or API-to-service.

Storage options:

- Relational tables.
- Vector index.
- Key-value store.
- Document store.
- Hybrid relational + vector memory.

This project does not yet implement long-term memory. Future extensions could add:

- Session memory table.
- User preference memory table.
- Query-result feedback table.
- Long-term verified fact table.

## 4. Storage Design

### 4.1 Short-Term Storage

Short-term memory should remain request-scoped.

Recommended storage:

- Java DTOs.
- Local execution context.
- Not persisted by default.

Reason:

- Fast.
- Simple.
- No data retention risk.

### 4.2 Working Memory Storage

Working memory can be stored in two modes:

- Runtime-only for normal requests.
- Persisted for debugging and evaluation.

Current project status:

- `executionTrace` is returned in `/ask` debug output.
- It is not stored in a database table.

Future option:

- Persist `traceId`, question, route decision, tool steps, and final answer for offline evaluation.

### 4.3 Long-Term Storage

Long-term memory should be explicit and controlled.

Possible schema:

- `MEMORY_ID`
- `USER_ID`
- `SESSION_ID`
- `MEMORY_TYPE`
- `CONTENT`
- `SOURCE`
- `CONFIDENCE`
- `CREATED_AT`
- `UPDATED_AT`
- `EXPIRES_AT`

Long-term memory should not silently store sensitive user data.

## 5. Memory Retrieval Strategy

Memory retrieval should depend on memory type.

Short-term retrieval:

- Direct object access.
- No ranking needed.

Working memory retrieval:

- Retrieve by current `traceId`.
- Retrieve previous step outputs by `toolType`.
- Use most recent successful tool result first.

Long-term retrieval:

- Filter by user or project scope.
- Filter by memory type.
- Rank by semantic similarity, recency, confidence, and task relevance.
- Prefer verified facts over weak observations.

For this project, a future memory retriever could use:

- Exact key lookup for schema facts.
- Relation lookup for entity memory.
- Keyword or vector search for conversational facts.

## 6. Memory Update Strategy

Memory update must be conservative.

Recommended update rules:

- Store only successful tool results by default.
- Do not persist fallback guesses.
- Do not persist uncertain answers without confidence.
- Update existing memory instead of duplicating it.
- Track source and timestamp.

Possible update triggers:

- User explicitly confirms an answer.
- Evaluation marks an answer as correct.
- A tool returns a high-confidence structured result.
- SQL metadata verifies a table schema.

For this project, good memory candidates include:

- Verified `TABLE_HAS_FIELD` facts.
- API-to-service dependencies.
- Repeatedly successful SQL metadata lookups.
- Failed query patterns that need better routing.

## 7. Memory Conflict Handling

Conflicts happen when two memories disagree.

Examples:

- One source says `CREATE_TIME`, another says `CREATED_AT`.
- One relation says field belongs to one table, another says a different table.
- SQL metadata disagrees with extracted document facts.

Conflict strategy:

- Prefer authoritative tools over weak extraction.
- Prefer SQL metadata over noisy text extraction for table schema.
- Prefer newer memory only when the source is equally trusted.
- Preserve conflicting facts with confidence scores instead of overwriting blindly.
- Expose conflicts in debug output when they affect the final answer.

For this project:

- `SQL_SEARCH` should outrank weak relation evidence for schema fallback.
- `TABLE_HAS_FIELD` from clean schema chunks should outrank `MENTIONED_WITH`.
- `RELATED_TO` should remain low confidence and should not override typed relations.

## 8. Memory Compression Design

Memory grows quickly if every trace and tool output is stored forever.

Compression goals:

- Reduce storage cost.
- Preserve useful facts.
- Remove duplicate noisy traces.

Compression strategies:

- Summarize repeated traces into one pattern.
- Keep final structured facts instead of raw intermediate text.
- Deduplicate equivalent memories.
- Age out low-confidence or unused memories.
- Keep raw trace only for failed or high-value cases.

Example:

Raw traces:

- Ten runs of `RAG_DOCUMENTS 表有哪些字段` with the same SQL result.

Compressed memory:

- `RAG_DOCUMENTS TABLE_HAS_FIELD ID, FILE_NAME, FILE_TYPE, CONTENT, STATUS, CREATE_TIME, DOCUMENT_TYPE`
- Source: `USER_TAB_COLUMNS`
- Confidence: high

## 9. Memory Benchmark Design

Memory should be evaluated separately from retrieval and generation.

Benchmark dimensions:

- Recall: does memory retrieve the right prior fact?
- Precision: does memory avoid irrelevant or stale facts?
- Freshness: does memory update when source data changes?
- Conflict handling: does memory choose the trusted source?
- Safety: does memory avoid storing sensitive data?
- Latency: does memory retrieval stay cheap enough for online `/ask`?

Candidate benchmark tasks:

- Ask the same schema question across sessions.
- Ask after a schema change.
- Ask with conflicting document and SQL metadata.
- Ask a user preference question after explicit confirmation.
- Ask a no-answer question and ensure no false memory is created.

For this project, a future memory benchmark can extend the existing eval framework:

- `router-only`
- `tool-agent`
- `tool-agent + memory`

## 10. Safety And Privacy Boundaries

Memory must not silently become a data leak.

Safety rules:

- Do not store secrets.
- Do not store database credentials.
- Do not store raw uploaded private documents as memory.
- Do not store user-specific data without explicit scope.
- Separate user memory, project memory, and global memory.
- Support deletion and expiration.
- Keep memory source and confidence visible.

For this project:

- `executionTrace` is safe as debug data only if it avoids secrets.
- SQL metadata is safe only for the current database schema scope.
- Long-term memory should require explicit design before persistence.

## 11. Mapping To Current Project

Current working-memory-like components:

- `AgentExecutionTrace`
- `AgentExecutionStep`
- `AgentRouteDecision`
- `AgentToolResult`
- `AskResponse.debug`

Current short-term context:

- `AgentToolInput`
- `relationHits`
- `chunkHits`
- `SqlTableColumnResult`

Future memory extensions:

- Session memory for multi-turn `/ask`.
- Long-term verified fact memory.
- Evaluation feedback memory.
- Failed retrieval memory for router improvement.

The current architecture is intentionally positioned for this evolution. The system already separates planning, tool execution, trace, and answer assembly, which makes memory an additive layer rather than a rewrite.

## 12. Interview Explanation

In an interview, this project can be explained this way:

The project has not implemented full persistent memory yet because the current priority is to make retrieval, graph reasoning, tool execution, and evaluation reliable first. If memory is added too early, it can store noisy facts, amplify retrieval mistakes, and introduce privacy risk before the core answer path is stable.

The current system already has a working-memory prototype. `executionTrace` records what the Agent tried, which tools were executed, whether each step succeeded, and which fallback path was used. `routeDecision` records the planned tool chain, and `toolResults` represent the intermediate task state. These are not long-term memory, but they are enough to explain one request and debug the Agent behavior.

The future design should split memory into short-term memory, working memory, and long-term memory. Short-term memory stays inside the current request. Working memory tracks the active execution state through `AgentExecutionTrace`. Long-term memory should only store validated, permission-safe, and source-backed facts. The hard part is not saving text; the hard part is deciding what is trusted, when to update, how to resolve conflicts, how to compress old traces, and how to prevent privacy leaks.
