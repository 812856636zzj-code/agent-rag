# Persistent fixed-eval history and regression comparison

## Problem

`POST /eval/run` evaluates the fixed question set and overwrites two `latest_*` files. A later run loses the previous result, so a change in citations, wrong answers, or abstentions cannot be traced to individual questions. The endpoint also has no stable identifier for a completed run. This prevents a reviewer from reproducing or explaining a regression after the latest report changes.

The clean Oracle bootstrap already supports one small first-party knowledge fixture. This proposal extends that verified path; it does not claim that all 15 questions are answered by the fixture.

## Existing behavior

- The question set is read from `docs/eval/questions.json` in the process working directory.
- Each case invokes baseline and graph answering, then records `EvalRunResult` fields.
- `EvalRunSummary` is returned to the caller, and `latest_eval_results.json` plus `latest_eval_report.md` are overwritten.
- Repeating the endpoint can change memory-backed results. The old run cannot be fetched or compared after the next call.

## Decision

Keep the existing `/eval/run` response fields and latest files for compatibility. Add a durable Oracle run record and an ordered snapshot of every case result. A run begins as `RUNNING`, becomes `COMPLETED` only after all cases and report outputs succeed, and becomes `FAILED` if processing stops. The case snapshot is immutable after completion. A SHA-256 digest of the question file makes mismatched datasets explicit.

Expose read-only history endpoints:

| Endpoint | Contract |
| --- | --- |
| `GET /eval/runs?limit=20&before=ID` | Newest first; bounded page and continuation cursor. |
| `GET /eval/runs/{id}` | Completed run summary and status; unknown ID is 404. |
| `GET /eval/runs/{id}/cases?limit=20&after=POSITION` | Ordered case snapshots with bounded pagination. |
| `GET /eval/compare?base=ID&candidate=ID` | Per-case improvement/regression flags and aggregate deltas; dataset mismatch is 409. |
| `GET /eval/gate?base=ID&candidate=ID` | Apply explicit regression budgets to the stored comparison; return a CI-readable decision and affected case IDs. |

The comparator examines the stored answer and source signals, not a rerun against current data. Missing or duplicate case identifiers in either snapshot are rejected. It separates new graph hits from lost graph hits, newly wrong answers, fixed wrong answers, newly missing citations, fixed citations, and new/recovered abstentions. A comparison of two identical runs has no directional changes, but the API does not assume repeat executions will be identical.

## Options considered

| Option | Problem | Outcome |
| --- | --- | --- |
| Keep timestamped files only | Hard to query, page, compare, or protect from mismatched question sets. | Rejected. |
| Save only aggregate counters | Hides which question regressed and which citation was lost. | Rejected. |
| Persist run metadata and complete case JSON snapshots | Supports exact historical replay and comparison without changing the answer pipeline. | Chosen. |

## Storage and failure behavior

Two new Oracle tables store run metadata and ordered case snapshots. A foreign key and uniqueness constraints prevent orphaned or duplicate case positions. The bootstrap gains these tables after the base document schema; existing dedicated test schemas use a separate additive migration. Database changes are additive and do not drop existing records.

The run and case snapshot are written in a transaction. Failed runs retain status and a bounded diagnostic message, but cannot be compared as successful evaluations. The latest file outputs remain compatibility artifacts; the database is the source of truth for historical queries. Unknown IDs and invalid pagination inputs receive client errors rather than empty success responses.

## Verification

The first test commit `a6d90f2` calls the live endpoint and fails because the response has no persisted `runId` (`1 test, 1 failure`). Implementation verification must include:

1. Oracle schema validation and a real 15-case `/eval/run` with an isolated output directory.
2. Fetching the run and all ordered cases after completion.
3. Comparing two runs using stored snapshots, including deterministic unit cases for improvements, regressions, citation loss, and abstention transitions.
4. Invalid IDs, dataset mismatch, pagination limits, immutable snapshots, and failed-run behavior.
5. The full offline Maven build with the opt-in Oracle test skipped by default.

The milestone size will be counted from its actual start and end commits after implementation. No code or test files will be added solely to meet the platform's line threshold.

## Verified implementation

- Red: `a6d90f2` added the live `/eval/run` assertion for a persisted ID. The Oracle-backed test had one failure because the old response had no `runId`.
- Green: `df92b0b` added the tables, persistence, pagination, and comparator. The focused Oracle test passed 1/1, deterministic comparator and loader tests passed 5/5, and `mvn -o -B clean verify` passed 18 ordinary tests with two opt-in Oracle tests skipped.
- Boundary follow-up: the same Oracle test now checks both cursor types, snapshot immutability, 400/404/409 responses, failed runs, and dataset mismatch. It passed 2/2 against the migrated test schema.
- Fresh schema: a separate `RAG_EVAL_HISTORY_20261013` user had zero tables before the unchanged `sql/bootstrap_oracle.sql`. Bootstrap exited 0 with no ORA errors and produced nine tables plus one seed chunk. The old `LocalEvalEndToEndTest` and new history test then passed 3/3 against this fresh schema. The test schema is local and contains no production data.
- Gate red: `8f7535a` required `/eval/gate` from the real endpoint; the test failed 1/1 with `expected 200 OK but was 404 NOT_FOUND`.
- Gate green: `5df796d` computes a strict or explicitly relaxed budget from stored comparison data without rerunning answers. The Oracle-backed history test passed 2/2 and four deterministic gate tests passed 4/4.

The database snapshots are the historical record. The two `latest_*` files are compatibility outputs and may be partially updated if a later filesystem write fails; a future change should stage and publish the pair together. This limitation does not alter stored completed snapshots.
