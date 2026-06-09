# Tool Permission Whitelist Design

## 1. Why Tool Permission Control Is Needed

Agent systems must control tool permissions because tool calls can affect data, services, and infrastructure:

- An Agent may call external APIs, databases, file systems, or business services.
- Some tools may have side effects, such as writing data, updating memory, or triggering workflows.
- User input and retrieved documents may contain prompt injection attempts.
- Different users and scenarios should not have the same tool permissions.
- Without permission boundaries, tool calling can cause data leakage, accidental operations, or unauthorized execution.

The key principle is that an Agent can propose a tool call, but the backend system must decide whether the call is allowed.

## 2. Current Project Situation

The current project is mainly a RAG / Agent demo. It already has several foundations for future tool safety:

- `AgentRouter` decides the retrieval and tool route.
- `AgentToolResult` records tool execution status, summary, data, error message, and latency.
- `AgentExecutionTrace` and `AgentExecutionStep` record the execution path.
- Tools such as graph search, chunk search, SQL metadata search, and answer generation already have clear tool boundaries.

The current tool calling capability is still a prototype. The project has not yet implemented a complete tool registry, permission whitelist, risk classification, approval workflow, or audit system.

## 3. Tool Registry Design

Future versions can introduce a `ToolRegistry` to manage all tools that the Agent is allowed to use.

Each tool metadata record should include at least:

- `toolName`
- `description`
- `inputSchema`
- `outputSchema`
- `riskLevel`
- `timeoutMs`
- `retryPolicy`
- `permissionScope`
- `enabled`
- `owner`
- `auditRequired`

Example tool categories:

- Search tool: chunk search and keyword search.
- Relation search tool: graph relation retrieval.
- Database read tool: read-only metadata query such as `USER_TAB_COLUMNS`.
- External API tool: calls to third-party systems.
- File read tool: controlled document or file inspection.
- Eval tool: batch evaluation jobs.
- Memory update tool: writes to session or long-term memory.

The registry should be the only source of truth for tool availability, risk level, timeout, and permission scope.

## 4. Permission Whitelist Design

Tool permission should not be decided freely by the model. It should be controlled by a backend whitelist.

Whitelist dimensions can include:

- User role.
- Endpoint.
- `routeDecision`.
- `toolName`.
- Environment.
- `riskLevel`.
- Session scope.

Example policy:

- Normal users can call search and ask tools.
- Eval tools are only available to developer or admin roles.
- Memory update tools require extra validation.
- Database write tools are disabled by default.
- Production disables debug-only tools.

This means the planner or router only selects candidate tools. The permission layer makes the final execution decision.

## 5. Tool Risk Levels

Tools should be classified by risk.

### LOW

Read-only and no side effects.

Examples:

- Chunk search.
- Relation search.
- SQL metadata read.

### MEDIUM

May consume more resources or call external services.

Examples:

- Eval batch.
- External search.
- Expensive multi-tool retrieval.

### HIGH

May involve user data, memory updates, file reads, or sensitive business APIs.

Examples:

- Memory update.
- Session history retrieval.
- Controlled file read.
- Internal business data lookup.

### CRITICAL

Can modify important state or trigger sensitive operations.

Examples:

- Database write.
- Delete operation.
- Permission change.
- External payment.
- Production configuration change.

The current project should default to allowing only `LOW` tools and selected `MEDIUM` tools. `HIGH` and `CRITICAL` tools should be disabled unless explicitly reviewed.

## 6. Parameter Validation

All tool calls must validate parameters before execution.

Validation should include:

- Required fields.
- Type check.
- Length limit.
- Enum validation.
- Path traversal protection.
- SQL injection protection.
- Prompt injection protection.
- Forbidden keyword check.

Model-generated tool arguments must never be executed directly. They must be treated as untrusted input and validated by backend code.

For example, `SQL_SEARCH` should not accept arbitrary SQL. It should only accept a validated table name and execute a fixed parameterized query.

## 7. Prompt Injection Defense

Users or documents may try to manipulate the Agent by injecting instructions such as:

- Ignore system rules.
- Call unauthorized tools.
- Leak debug information.
- Modify memory.
- Skip permission checks.

Defense strategy:

- Tool permission is checked by backend code, not by prompt text.
- Document content is treated as evidence, not instruction.
- Every tool call goes through permission check before execution.
- High-risk tools require human approval or remain disabled.
- `AgentExecutionTrace` records suspected injection or rejected tool attempts.

The system should separate evidence from instructions. Retrieved content can support an answer, but it must not change the tool permission policy.

## 8. Execution Flow

Safe tool execution should follow this flow:

```text
User Question
-> AgentRouter
-> Route Decision
-> Candidate Tool Selection
-> Permission Whitelist Check
-> Parameter Validation
-> Timeout / Rate Limit Check
-> Tool Execution
-> Result Sanitization
-> AgentExecutionTrace Logging
-> Answer Generation
```

If any safety step fails, the tool should not execute. The response should either fall back to a safer path or return a controlled failure.

## 9. Result Sanitization

Tool results should not be sent directly to the model or user without cleanup.

Sanitization should include:

- Remove sensitive fields.
- Limit returned length.
- Remove internal error stacks.
- Filter tokens, passwords, and secrets.
- Keep only fields needed for source and evidence.
- Convert failures to a unified error format.

For RAG answers, the sanitized result should preserve `source`, `evidence`, `documentId`, `chunkId`, relation path, and confidence when available.

## 10. Audit And Trace

Every tool call should be auditable.

Audit fields should include:

- `requestId`
- `userId` or anonymous user identifier
- `toolName`
- Input summary
- Permission decision
- `riskLevel`
- Execution status
- Latency
- Error message
- Fallback action

Current project mapping:

- `AgentExecutionTrace` can store the tool call timeline.
- `AgentExecutionStep` can store per-step status, input summary, output summary, error, and latency.
- `AgentToolResult` can store execution success, data summary, error message, and latency.

Future versions can extend these structures to record permission decision and risk level.

## 11. Fallback Strategy

When a tool call is rejected or fails, the system should degrade safely:

- Unauthorized tool: return controlled failure and do not execute the tool.
- Invalid parameters: return parameter validation failure.
- Tool timeout: fall back to normal RAG answer.
- Tool circuit breaker open: use cached result or baseline answer.
- High-risk tool: require human approval or keep it disabled.

Fallback should be visible in trace. The user-facing answer should avoid exposing internal stack traces or sensitive permission policy details.

## 12. Mapping To Current Project

Future implementation can map tool permission control to the current project:

- `AgentRouter`: selects candidate tools, but should not decide final permission.
- `ToolRegistry`: future component for tool metadata, risk level, timeout, retry, and permission scope.
- `AgentToolResult`: carries execution result, status, error, and latency.
- `AgentExecutionTrace`: records permission check, validation, execution, fallback, and rejected tool calls.
- `SearchService`: can be exposed as a `LOW` risk read-only tool.
- `RelationSearchService`: can be exposed as a `LOW` risk read-only graph retrieval tool.
- `EvalService`: should be treated as a `MEDIUM` risk batch tool.
- Memory design: memory update should be treated as a `HIGH` risk tool.

This design keeps the Agent flexible while preventing the model from directly controlling sensitive operations.

## 13. Interview Explanation

In an interview, this project can be explained this way:

Agent tool calling cannot rely only on prompt constraints. Prompts can be ignored, injected, or misunderstood. Real permission control should live in backend code through a `ToolRegistry`, permission whitelist, risk levels, parameter validation, timeout control, and audit trace.

The model or planner can propose candidate tool calls, but the system decides whether the tool is enabled, whether the user has permission, whether parameters are valid, and whether the risk level is acceptable. If a tool is denied or fails, the system should fall back to a safer retrieval path or return a controlled failure.

The current project has not implemented a full tool permission system yet, but it already has the right extension points: `AgentToolResult` for tool outcomes and `AgentExecutionTrace` for step-level observability. These can evolve into a complete tool governance layer without changing the core RAG architecture.
