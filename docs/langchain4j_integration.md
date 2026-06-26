# LangChain4j Minimal Integration

## Why Only Answer Generation

This project already has a working RAG pipeline with deterministic retrieval, graph relation recall, scoring, rerank, trace, memory, and eval diagnostics. LangChain4j is therefore integrated only at the answer generation layer.

The goal is to add a real LLM answer provider without replacing the existing retrieval quality loop. Query rewrite, recall, scoring, rerank, source assembly, and eval remain owned by the Spring Boot backend.

## Current Call Chain

```text
/ask
-> AskController
-> AskService
-> AgentRouterService
-> AgentExecutor
-> MemoryService.findRelevantMemories
-> ChunkSearchTool / GraphSearchTool / SqlSearchTool
-> QueryRewriteService
-> SearchService / RelationSearchService
-> RetrievalScoreService
-> RerankService
-> AnswerGenerateTool
-> AgentAnswerAssembler builds structured AskResponse
-> LlmAnswerClient
   -> RuleBasedAnswerClient
   -> LangChain4jAnswerClient
-> AskResponse debug / AgentExecutionTrace
-> MemoryService.saveFromTrace
```

LangChain4j appears only behind `LlmAnswerClient`. The business chain does not expose LangChain4j types.

## Rule Mode vs LangChain4j Mode

| Mode | Provider | Behavior |
| --- | --- | --- |
| `answer.mode=rule` | `RULE` | Uses existing `AgentAnswerAssembler` result. This is the default and preserves current behavior. |
| `answer.mode=langchain4j` + enabled | `LANGCHAIN4J` | Uses LangChain4j `OpenAiChatModel` to rewrite only the answer text based on backend-provided evidence. |
| LangChain4j failure | `RULE` with `fallbackUsed=true` | Falls back to existing rule-based answer. `/ask` and `/eval/run` should not fail because the model call failed. |

## Timeout, Fallback, and Trace

LangChain4j calls use `answer.langchain4j.timeoutMs`. If the key is blank, the model call times out, or any runtime exception happens, `LangChain4jAnswerClient` returns the rule-based answer when `fallbackEnabled=true`.

The following fields are recorded in `AskDebugInfo` and `AgentExecutionTrace`:

- `answerProvider`
- `answerModelName`
- `answerLatencyMs`
- `answerFallbackUsed`
- `answerErrorMessage`

This keeps model behavior observable and lets `/eval/run` compare answer providers without changing hit-rate calculations.

## Why Sources Stay Backend-Owned

Sources are still returned from backend structures:

- `chunks`
- `sources`
- `relationPaths`
- SQL metadata source

The model is explicitly instructed not to invent or rewrite sources. This prevents LLM-generated citation drift and keeps source citation auditable. LangChain4j can only rewrite `answer`; it does not own `sources`.

## Why Recall, Rerank, and Eval Are Not Replaced

The project's core value is the controlled RAG loop:

- Query rewrite and search query expansion.
- Multi-route chunk and relation recall.
- Candidate scoring with score detail.
- Rerank diagnostics.
- Fixed eval set with baseline vs graph comparison.
- Failure diagnosis and trace-derived memory.

Replacing these with framework defaults would reduce explainability and make evaluation regressions harder to locate.

## Real Model Verification

### 1. Configure `OPENAI_API_KEY`

PowerShell:

```powershell
$env:OPENAI_API_KEY="sk-..."
```

Or set it in your system environment. Do not commit API keys to `application.yml`.

### 2. Start LangChain4j Mode

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run -Dspring-boot.run.arguments="--answer.mode=langchain4j --answer.langchain4j.enabled=true"
```

Optional overrides:

```text
--answer.langchain4j.modelName=gpt-4o-mini
--answer.langchain4j.timeoutMs=8000
--answer.langchain4j.maxTokens=800
```

### 3. Verify `/ask`

```powershell
$body = @{ question = '/ask 依赖哪些核心服务？'; mode = 'graph' } | ConvertTo-Json
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/ask -ContentType 'application/json' -Body $body
$r.debug.answerProvider
$r.debug.answerModelName
$r.debug.answerFallbackUsed
$r.sources.Count
```

Expected with a valid key:

- `answerProvider=LANGCHAIN4J`
- `answerModelName` equals configured model name
- `answerFallbackUsed=false`
- `sources` still populated by backend

### 4. Verify Fallback

Use an invalid key or very low timeout:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18094 --answer.mode=langchain4j --answer.langchain4j.enabled=true --answer.langchain4j.apiKey=invalid-test-key --answer.langchain4j.timeoutMs=1000"
```

Then call `/ask`. Expected:

- request succeeds
- `answerProvider=RULE`
- `answerFallbackUsed=true`
- `answerErrorMessage` records the model failure
- `sources` are still returned

### 5. Run `/eval/run`

```powershell
$summary = Invoke-RestMethod -Method Post -Uri http://localhost:8080/eval/run
$summary.total
$summary.graphHitRate
$summary.sourceHitRate
$summary.results[0].answerProvider
$summary.results[0].fallbackUsed
```

`/eval/run` should complete even if LangChain4j fails and falls back.

### 6. Compare Rule and LangChain4j

Run eval twice:

1. `answer.mode=rule`
2. `answer.mode=langchain4j`

Compare:

- `graphHitRate`
- `sourceHitRate`
- noise count
- average `answerLatencyMs`
- `fallbackUsed` count
- missing expected keywords count

Use `docs/eval/langchain4j_eval_comparison.md` as the report template.

## Extension Plan

### Step 1: Real LLM Answer Generation

Keep the current integration. Only replace answer text after backend evidence assembly.

### Step 2: Optional Tool Calling Adapter

Adapt existing `AgentTool` implementations to LangChain4j tools if needed:

- `ChunkSearchTool`
- `GraphSearchTool`
- `SqlSearchTool`

Do not replace `AgentExecutor` until trace and eval prove that a LangChain4j agent improves outcomes.

### Step 3: Optional Embedding

Add an `EmbeddingClient` interface and a separate vector recall branch only after current keyword/entity/relation recall remains stable. Do not replace existing recall.

## Interview Explanation

This project did not use LangChain4j as a full RAG framework. It integrates LangChain4j only at the LLM answer generation layer. The retrieval chain remains self-built because it has custom query rewrite, relation recall, scoring, rerank, source citation, eval diagnostics, and trace-derived memory. This keeps the system controllable while still adding real LLM capability.
