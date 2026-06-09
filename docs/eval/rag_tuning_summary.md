# RAG Tuning Summary

## 目标

本轮 RAG 调优的目标是把问答链路从“只看最终命中率”推进到“固定评测集、真实执行、可诊断、可迭代”的闭环。评测集固定为 `docs/eval/questions.json` 的 15 题，覆盖 RAG、entity、relation、graph、sources、noise 场景；每轮都通过 `/eval/run` 真实调用 baseline ask 和 graph ask，不 mock 答案。

## v0-v3 调优过程

| 轮次 | baselineHitRate | graphHitRate | sourceHitRate | 关键诊断结论 |
| --- | --- | --- | --- | --- |
| v0 固定评测集前 | - | - | - | 缺少稳定评测集和可复现报告，只能看单次问答效果，无法判断 graph ask 是否真的优于 baseline。 |
| v1 固定评测集 + 真实 eval | 0.60 | 0.6667 | 0.60 | 建立 `questions.json`、`/eval/run`、`latest_eval_results.json`、`latest_eval_report.md`；graph 只小幅优于 baseline，主要问题是召回和诊断信息不足。 |
| v2 Query Rewrite + Scoring + Rerank + 多路 Recall | 0.60 | 0.7333 | 0.6667 | `REWRITE_NO_EFFECT` 从 15 降到 2，`NO_CANDIDATES_RECALLED` 从 5 降到 4；说明 rewrite 和多路 recall 开始生效，但候选质量仍不足。 |
| v3 候选集质量分析 + Recall 扩容 | 0.60 | 0.9333 | 0.80 | `NO_CANDIDATES_RECALLED` 从 4 降到 0；最终 `RERANK_NO_EFFECT=14` 的主因被定位为单候选，而不是 rerank 算法本身失效。 |

## 为什么 v1 没明显提升

v1 的核心价值是把评测链路跑起来，而不是直接优化检索算法。它固定了 15 题评测集，真实调用 baseline 和 graph，并生成结果与报告，但 graph ask 的召回路径、query rewrite、候选评分、rerank 证据都还比较弱。

当时 graphHitRate 只有 0.6667，sourceHitRate 只有 0.60。失败原因主要集中在没有稳定召回候选、rewrite 对查询几乎没有扩展、source/evidence 无法解释命中来源。换句话说，v1 解决的是“能不能评”，还没有充分解决“怎么召回更准、怎么排序更好”。

## 为什么 v2/v3 开始提升

v2 增加了规则化 Query Rewrite、打分和 rerank，并把 graph ask 接入 rewrite/scoring/rerank。rewrite 会输出 `rewrittenQuery`、`expandedKeywords`、`searchQueries`、`detectedIntent`、`detectedEntities`，让原问题不再只按字面检索；多路 recall 会按原问题、同义词扩展、实体+意图组合召回候选。

v2 的直接效果是 rewrite 失效明显下降，graphHitRate 从 0.6667 到 0.7333，sourceHitRate 从 0.60 到 0.6667。这说明 query rewrite 和多路 recall 确实补到了部分 graph/entity/relation 场景。

v3 继续把候选上限从 30 扩到 50，每个 searchQuery 保留 topN=10，并在空召回时增加单实体、单扩展词、intent keyword fallback。最终 `NO_CANDIDATES_RECALLED` 从 4 降到 0，graphHitRate 到 0.9333，sourceHitRate 到 0.80。

同时 v3 没有把 `RERANK_NO_EFFECT=14` 简单归因给 rerank，而是增加候选质量字段后发现：平均 rerank 前候选数只有 1.20，13/15 题是单候选，2/15 是分数过平。也就是说，rerank 大多数时候没有可比较对象，下一步应该优先扩大候选多样性。

## 最终效果

- 固定评测集：15 题。
- baselineHitRate：0.60。
- graphHitRate：0.9333。
- sourceHitRate：0.80。
- `NO_CANDIDATES_RECALLED`：4 -> 0。
- `REWRITE_NO_EFFECT`：15 -> 2。
- `SOURCE_NOT_MATCHED`：6 -> 3。
- `ANSWER_MISSING_EXPECTED_KEYWORDS`：13 -> 10。
- 候选质量结论：`SINGLE_CANDIDATE=13`，`FLAT_SCORES=2`，`RERANK_NO_EFFECT` 的真实主因是候选集不够丰富。

## 面试可讲版本

我做这轮 RAG 优化时，先没有直接改算法，而是先搭了一个可复现的 eval 闭环：固定 15 道覆盖 RAG、实体、关系、图增强、来源和噪声的评测题，每次通过 `/eval/run` 真实调用 baseline ask 和 graph ask，并把命中率、source 命中率、失败原因和样例写入 JSON/Markdown。

第一版 graph 只比 baseline 略好，问题在于缺少 query rewrite 和多路召回，很多问题召回不到候选。第二版加入规则化 Query Rewrite、Scoring、Rerank 和多路 Recall 后，graphHitRate 从 0.6667 提升到 0.7333。第三版继续扩容候选集，并在空召回时做单实体、单扩展词、意图词 fallback，最终把 `NO_CANDIDATES_RECALLED` 从 4 降到 0，graphHitRate 提升到 0.9333，sourceHitRate 提升到 0.80。

更重要的是，我没有只看最终命中率，而是增加了候选质量诊断。报告显示 `RERANK_NO_EFFECT` 仍然有 14 个，但其中 13 个是单候选，所以真正的问题不是 rerank 排序不工作，而是候选多样性不够。这个结论直接决定了后续优化优先级：先增强 recall 和候选覆盖，再调 rerank 权重。

## 后续优化方向

1. 扩大评测集：从 15 题扩到更多业务真实问题，按 RAG、entity、relation、graph、sources、noise 分层统计，避免小样本偶然提升。
2. 提升候选多样性：当前平均候选数只有 1.20，后续应优化 recall depth、分路召回配额、去重策略和 source-aware recall，让 rerank 有足够候选可比较。
3. 优化 evidence/source：当前仍有 `SOURCE_NOT_MATCHED=3`，需要让答案引用的 source 与 expectedSource 更稳定对齐，尤其是字段表、证据来源、噪声判断类问题。
4. 优化 answer assembly：当前 `ANSWER_MISSING_EXPECTED_KEYWORDS=10`，说明候选可能已经存在，但答案组织没有把预期关键词、关系证据或字段名完整表达出来。
5. 保留 eval 作为回归门禁：后续任何 recall、rerank、answer 改动都应先跑 `/eval/run`，确认 graphHitRate、sourceHitRate 和诊断原因没有回退。
