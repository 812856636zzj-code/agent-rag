# Evidence-grounded answer milestones

This document records the problem, choice, implementation, and verification behind three adjacent development intervals. The layout follows the decision trail illustrated by the [PaddlePaddle API design RFC](https://github.com/PaddlePaddle/community/blob/master/rfcs/APIs/20250116_api_design_for_baddbmm.md); that RFC is a writing reference, not this project's acceptance test.

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
- End: `ab6ba60`.
- Goal: preserve legacy hit metrics but add `wrongAnswer`, `citationMatched`, `citationMissing`, and `abstained` per question, counts in the JSON summary, and columns in the Markdown report.
- Failing test commit: `ef8e1ae`, `EvalQualityMetricsTest` showed 2 failures out of 2 tests at the start state. A graph response with answer `USERS has NAME` was previously considered a hit for expected `ORDERS AMOUNT` because those words appeared only in retrieved context/content.
- First implementation: `8736904`. Focused tests passed 2/2, and `mvn -o -B clean verify` passed 11/11 and built the JAR.
- Additional failing test commit: `b01b7f8`, `rejectsUnrelatedEvidenceEvenWhenDocumentNameMatchesExpectedSource`. The focused suite then reported 3 tests, 1 failure: a matching `ORDERS.md` filename hid an unrelated `USERS has NAME` evidence sentence.
- Final fix: `ab6ba60`. `mvn -o -B -Dtest=EvalQualityMetricsTest test` passed 3/3. A citation now needs nonblank evidence containing at least one expected answer keyword plus a match to an expected source token in the source name or evidence. When no expected source token is specified, relevant evidence alone is sufficient.
- Scope: `EvalRunResult.java`, `EvalRunSummary.java`, `EvalServiceImpl.java`, and `EvalQualityMetricsTest.java` only. Later database configuration and documentation commits are outside this interval.

## Verification and limits

All commands above used JDK 17 and Maven with a populated local dependency cache. A fresh offline cache initially failed because Spring Boot's parent POM was absent; one online `mvn -B clean verify` fetched dependencies, after which offline builds succeeded. This does not establish that a clean air-gapped machine can build immediately.

The fixed `/eval/run` endpoint has not been run against a live Oracle database for these changes. Existing `docs/eval/latest_eval_results.json` and `docs/eval/latest_eval_report.md` are historical snapshots. Running `/eval/run` with a prepared database and fixed dataset will regenerate them; until then, new count values are not claimed.
