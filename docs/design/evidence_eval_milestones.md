# Evidence-grounded answer milestones

This document records the problem, choice, implementation, and verification behind four adjacent development intervals. The layout follows the decision trail illustrated by the [PaddlePaddle API design RFC](https://github.com/PaddlePaddle/community/blob/master/rfcs/APIs/20250116_api_design_for_baddbmm.md); that RFC is a writing reference, not this project's acceptance test.

**Qualification audit:** these are genuine red/green development checkpoints, but none currently meets the supplied platform threshold of more than 10 changed code files and more than 2,000 changed code lines in one interval. Do not submit them as qualifying milestones or split their test and implementation commits into separate milestones. Counts below use changed `.java` and `.yml` files and added plus deleted lines; documentation and generated files are excluded.

| Interval | Changed code files | Changed code lines | Meets size threshold? |
| --- | ---: | ---: | --- |
| 1 | 2 | 64 | No |
| 2 | 3 | 156 | No |
| 3 | 5 | 316 | No |
| 4 | 2 | 60 | No |

The external behavior and tests described below are real. A future qualifying milestone needs a coherent requirement whose implementation naturally spans the required scope, with a failing assertion at its start and a passing assertion at its end; commit count alone does not establish that.

## Problem and existing behavior

The RAG pipeline could select a `USERS` field sentence as evidence for an `ORDERS` question. It could then generate a confident answer even when retrieved chunks had no usable evidence sentence. Finally, fixed eval counted keywords in retrieved context and raw source content as a hit, so a wrong answer could appear successful. The historical fixed report did not separately show wrong answers, citation failures, or abstentions.

## Choices

| Option | Result | Decision |
| --- | --- | --- |
| Add more prompt instructions | Model output remains non-deterministic and cannot repair wrong evidence selection. | Rejected for the guard. |
| Require every retrieved hit to contain evidence | One noisy hit could suppress a valid answer supported by another hit. | Rejected. |
| Select relevant evidence, abstain if all selected evidence is empty, and score answer/citation quality separately | Keeps the serving guard deterministic and exposes remaining quality gaps in eval. | Implemented. |

`wrongAnswer` and `citationMatched` are keyword-based review signals. They do not prove semantic truth or logical entailment.

## Milestone 1: prevent cross-table evidence

- Start: `60c58c78c15e64b06ff526e8a120dd6179775c98` (the pre-development baseline).
- End: `10a8dbd237c42c9abd1d941308a11a0f20322968`.
- Goal: asking for `ORDERS` fields must not select a `USERS` field sentence as evidence.
- Failing test commit: `64824bc`, `EvidenceSelectorImplTest.doesNotUseAnotherTablesFieldsAsEvidence`. At the start state with the test added, `mvn -o -B -Dtest=EvidenceSelectorImplTest test` reported 2 tests, 1 failure.
- Fix commit: `10a8dbd`. `mvn -o -B clean verify` passed 6 tests and built the JAR.
- Scope: `EvidenceSelectorImpl.java` and its test only.

## Milestone 2: abstain when retrieval has no evidence

- Start: `10a8dbd237c42c9abd1d941308a11a0f20322968` (exactly milestone 1's end).
- End: `e2976dfb23d07e46be9ecbef844c753912a79f56`.
- Goal: when all retrieved sources have no nonblank evidence sentence, return `NO_ANSWER`, omit unsupported sources, skip answer model generation, and keep retrieval diagnostics.
- Failing test commit: `ba30761`, `AnswerGenerateToolTest.abstainsWhenChunksHaveNoSupportingEvidence` and `doesNotCallModelWhenChunksHaveNoSupportingEvidence`. At the start state with these tests added, `mvn -o -B -Dtest=AnswerGenerateToolTest test` reported 4 tests, 2 failures.
- Fix commit: `e2976df`. `mvn -o -B clean verify` passed 9 tests and built the JAR, including a cross-component evidence selection test.
- Scope: `AgentAnswerAssembler.java`, `AnswerGenerateTool.java`, and `AnswerGenerateToolTest.java` only.

## Milestone 3: expose wrong answers and citation gaps in fixed eval

- Start: `e2976dfb23d07e46be9ecbef844c753912a79f56` (exactly milestone 2's end).
- End: `a624a86b57823fe22842d919ca43136a3ad3107c`.
- Goal: preserve legacy hit metrics but add `wrongAnswer`, `citationMatched`, `citationMissing`, and `abstained` per question, counts in the JSON summary, and columns in the Markdown report.
- Failing test commit: `ef8e1ae`, `EvalQualityMetricsTest` showed 2 failures out of 2 tests at the start state. A graph response with answer `USERS has NAME` was previously considered a hit for expected `ORDERS AMOUNT` because those words appeared only in retrieved context/content.
- First implementation: `8736904`. Focused tests passed 2/2, and `mvn -o -B clean verify` passed 11/11 and built the JAR.
- Additional failing test commit: `b01b7f8`, `rejectsUnrelatedEvidenceEvenWhenDocumentNameMatchesExpectedSource`. The focused suite then reported 3 tests, 1 failure: a matching `ORDERS.md` filename hid an unrelated `USERS has NAME` evidence sentence.
- Final fix: `ab6ba60`. `mvn -o -B -Dtest=EvalQualityMetricsTest test` passed 3/3. A citation now needs nonblank evidence containing at least one expected answer keyword plus a match to an expected source token in the source name or evidence. When no expected source token is specified, relevant evidence alone is sufficient.
- Final interval verification: at `a624a86`, `mvn -o -B clean verify` passed 12/12 tests and built the JAR.
- Scope: the eval implementation changes are in `EvalRunResult.java`, `EvalRunSummary.java`, `EvalServiceImpl.java`, and `EvalQualityMetricsTest.java`. The interval also contains `e120c83` (externalized a pre-existing fixed database password), `3e75db8` (documented these milestones), `4ae1536` (documented setup and eval outputs), and `a624a86` (recorded five future TODOs). The credential change is unrelated to eval scoring; it was made while preparing the repository for publication and is called out here rather than presented as part of the metric fix.

## Milestone 4: suppress unsupported citations in mixed evidence

- Start: `a624a86b57823fe22842d919ca43136a3ad3107c` (exactly milestone 3's end).
- End: `02015ce1ea5cc5f15fb303132dbd7c4adb7e9c28`.
- Goal: when one retrieved source has a nonblank evidence sentence and another has only blank evidence, keep the answer and supported citation, remove the unsupported citation from the response and the model's source preview, and preserve both retrieval hits in diagnostics.
- Failing test commit: `7159596`, `AnswerGenerateToolTest.removesUnsupportedCitationFromMixedEvidenceBeforeModelGeneration`. At the start state with this test added, `mvn -o -B -Dtest=AnswerGenerateToolTest test` reported 6 tests, 1 failure: expected 1 published source but got 2.
- Fix commit: `02015ce`. The same focused command passed 6/6; `mvn -o -B clean verify` passed 13/13 and built the JAR.
- Scope: `AnswerGenerateTool.java` and `AnswerGenerateToolTest.java` only.
- Limit: this interval filters citation objects. `buildLlmAnswerRequest` still passes raw `topChunks`, `relationEvidence`, and `structuredContext` to `LangChain4jAnswerClient.buildPrompt`, even if a corresponding citation was filtered. Separately constraining those prompt inputs and regenerating rule-based answers from supported evidence remains a future task.

## Verification and limits

All commands above used JDK 17 and Maven with a populated local dependency cache. A fresh offline cache initially failed because Spring Boot's parent POM was absent; one online `mvn -B clean verify` fetched dependencies, after which offline builds succeeded. This does not establish that a clean air-gapped machine can build immediately.

The fixed `/eval/run` endpoint has not been run against a live Oracle database for these changes. Existing `docs/eval/latest_eval_results.json` and `docs/eval/latest_eval_report.md` are historical snapshots. Running `/eval/run` with a prepared database and fixed dataset will regenerate them; until then, new count values are not claimed.
