# LangChain4j Eval Comparison

This report compares the default rule-based answer provider with the optional LangChain4j answer provider. Retrieval, scoring, rerank, sources, and eval hit-rate logic are unchanged.

| mode | provider | total | baselineHitRate | graphHitRate | sourceHitRate | avgLatencyMs | fallbackCount | noiseCount | missingExpectedKeywordsCount | conclusion |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| rule | RULE | 15 | 0.6 | 0.9333 | 0.8 | TBD from latest_eval_results.json | 0 | TBD from latest_eval_results.json | TBD from latest_eval_results.json | Baseline mode. Current default behavior is preserved. |
| langchain4j | LANGCHAIN4J |  |  |  |  |  |  |  |  | Pending valid `OPENAI_API_KEY` real-model eval. |
| langchain4j-fallback | RULE | 15 | 0.6 | 0.9333 | 0.8 | TBD from fallback run | 15 expected if every model call fails | TBD | TBD | Local failure-path validation only; not a real model quality result. |

## Current Rule Result

The current committed eval result was generated with:

```text
answer.mode=rule
answer.langchain4j.enabled=false
```

Observed:

- `total=15`
- `baselineHitRate=0.6`
- `graphHitRate=0.9333`
- `sourceHitRate=0.8`
- `provider=RULE`
- `fallbackUsed=false`

## Real LangChain4j Result

No real model result is filled yet because this workspace does not include a valid external model key. To fill it:

1. Set `OPENAI_API_KEY`.
2. Start with `--answer.mode=langchain4j --answer.langchain4j.enabled=true`.
3. Run `POST /eval/run`.
4. Copy the generated metrics from `docs/eval/latest_eval_results.json`.
5. Update the `langchain4j` row above.

## Comparison Checklist

- Graph hit rate should not materially regress.
- Source hit rate should not materially regress.
- Noise count should not increase.
- `fallbackCount` should be zero for a healthy real-model run.
- Average latency should be reviewed separately from answer quality.
- Any graph/source regression should be traced through `answerProvider`, `answerFallbackUsed`, `answerLatencyMs`, and `answerErrorMessage`.
