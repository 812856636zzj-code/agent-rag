# Selected evidence as the answer boundary

## Problem

`AnswerGenerateTool` removed citations without a nonblank evidence sentence,
but its `LlmAnswerRequest` still copied the original chunk and relation hit
lists. `LangChain4jAnswerClient.buildPrompt` printed their raw text and the
preassembled structured context. The rule client, including a model fallback,
also reused the preassembled answer. A dropped citation therefore did not
prevent the unsupported text from influencing the final answer.

## Existing behavior and alternatives

The earlier citation filter correctly retained one supported source when a
second retrieval hit had no evidence. It preserved both hits in retrieval
diagnostics. A stronger prompt instruction would still expose the rejected
text to the model. Removing all retrieved text would suppress valid answers.
The chosen boundary keeps selected evidence sentences and their stable source
metadata, while leaving raw retrieval diagnostics outside answer generation.

## Design

`SupportedEvidenceContext` is built from `AskResponse.sources` after the
assembler has selected evidence sentences. It copies only sources with
nonblank sentences, trims and deduplicates those sentences, and replaces each
published source's content with their joined evidence text. This projection is
the sole input for:

- synthetic chunk and relation evidence in `LlmAnswerRequest`;
- structured context sent to the model and returned with the answer;
- the deterministic rule answer and model-failure fallback;
- relation-path evidence and summary, where source provenance and entity names
  match the selected sentence.

The projection never copies `AgentToolInput.chunkHits`, `relationHits`, or the
assembler's prebuilt answer into a model or rule request. A blank model answer
leaves the safe evidence-derived answer in place. When no source has evidence,
the existing `EVIDENCE_GUARD` abstention runs without calling the model.
Retrieval counts and diagnostic chunks remain available for debugging; they
are not assertions that every recalled hit supports the answer.

## Test history

Start commit: `d99b030`. Test commit: `b856cd3`.
`mvn -o -B -Dtest=SupportedEvidenceBoundaryTest test` at the start produced
three tests and three failures: raw chunks leaked, raw relation/other chunk
context leaked, and rule mode returned a preassembled unsupported answer.

The implementation commit `96e6f31` projects selected evidence into both
answer providers. A later edge-case assertion caught matching a relation path
only by chunk ID when the document ID differed; the fix requires every shared
identifier to agree. The final focused suite ran 12/12 tests. The final
`mvn -o -B clean verify` ran 30 tests: 28 passed and two opt-in Oracle tests
were skipped. The local Docker daemon currently has no Oracle container or
cached Oracle image, so a fresh live `/eval/run` was not rerun for this branch.

## Impact and limits

The externally visible answer, citation content, structured context, and
relation paths can change when the old assembler included text outside the
selected evidence. The model prompt now uses the same evidence boundary.
This does not establish that the selected sentence semantically entails every
model answer. It also does not hide raw retrieval data from diagnostic fields.

This is a genuine independent development interval. Its size must be measured
from `d99b030` before any platform submission; it must not be represented as
meeting a >10-code-file and >2,000-changed-line milestone threshold merely
because its red/green tests pass.
