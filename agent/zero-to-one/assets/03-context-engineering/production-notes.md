# 第 03 篇配图制作记录

日期：2026-10-18。对应正文：[上下文工程](../../03-context-engineering.md)。

## 目的与依据

三张图分别解释连续前缀复用、Skills 渐进式披露、完整工具批次压缩。采用系列既有小天角色与暖色手绘风格，用不同布局表达机制；没有复制参考书的插图。

- 理论主线：[《深入理解 AI Agent》第二章](https://bojieli.github.io/ai-agent-book/astro/book/chapter2/)。
- 缓存机制补充：[Hugging Face 缓存说明](https://huggingface.co/docs/transformers/main/en/cache_explanation)。
- Skills 分层：[Agent Skills 规范](https://agentskills.io/specification)。
- 实现核对：[ContextSession.java](../../first-agent/src/main/java/example/agent/context/ContextSession.java)、[SkillTools.java](../../first-agent/src/main/java/example/agent/context/SkillTools.java)。
- 角色输入：本地 xiaotian-visual Skill 的 character-style-reference.png，仅用于形象参考；参考图不复制到项目。
- 使用内置 image_gen 工具，生成不透明 PNG；第一张经过一次局部颜色修订。生成原文件保留在工具输出目录，最终图复制到本目录。

## 图与核验

| 文件 | 解释的关系 | 已检查的重点 |
|---|---|---|
| [01-prefix-cache.png](01-prefix-cache.png) | 前两次相同前缀；第三次从开头变化处起，后续状态通常需重算 | 中文标签清楚；第三行全部为橙色；没有把后续相同文字误标成可复用；脚注说明实际命中依赖服务端 |
| [02-progressive-skills.png](02-progressive-skills.png) | 目录 → 选中指南 → 需要时才读取资源 | 未选中的指南未打开；资源层使用虚线；脚注明确本例只实现前两级；人物没有遮挡标签 |
| [03-context-compaction.png](03-context-compaction.png) | 旧完整批次 → 去重原始观察；最近批次保留调用与全部结果 | 来源、事实、未知项与失败均在；原始问题与约束保留；注明原批次尚未持久化；正文解释标签不等于已实现结构化解析 |

检查方式：逐张查看生成图；核对文字、箭头、颜色含义、人物手臂与遮挡，再检查正文图注和实现边界。三张 PNG 的实际尺寸均为 1672 × 941，已核对正文相对路径与文件存在性。图 1 初稿第三行仍有蓝色块，可能造成复用歧义，已将第三行统一改为橙色后采用。其余两张采用首次输出。下列英文内容是实际提交的生成提示词，不是正文的一部分。

## 图 1：首次生成提示词

```text
Create a polished educational illustration for a Chinese Java/AI Agent technical article, a wide landscape 16:9 composition, high-resolution PNG. This is a NEW ORIGINAL diagram, not an edit of the reference. Use the attached image only for Xiaotian's identity and warm hand-drawn anime style; do NOT reproduce its grid.
Topic: prefix caching depends on an identical prefix of the ACTUAL model token sequence. Explain visually how appending a result preserves a candidate reusable prefix, while changing the first block means recomputing from the change onwards. This is an abstract teaching diagram, no measured performance claims.
Warm ivory paper with very subtle texture, elegant ink outlines, subdued teal/blue for stable prefix, warm orange for new/recomputed parts. Lots of white space. Fine professional educational drawing, not corporate template cards. Chinese labels large and perfectly legible at article width. No giant title.
Main graphic fills the upper 80%, aligned horizontal token-block strips:
Row 1 left label “请求一”. Four equal adjoining blocks: “规则 A”, “工具”, “问题”, “历史”.
Row 2 left label “请求二”. Exactly the same first four blocks, then one orange block “新结果”. Align all shared blocks vertically with row 1. One teal bracket spanning the FOUR unchanged blocks only, with exact label “相同前缀：具备复用条件”. Small orange label pointing only to the appended block: “新增内容”.
Row 3 left label “请求三”. First block now “规则 B”, followed by “工具”, “问题”, “历史”, “新结果”. Mark the FIRST block with a small orange change mark, shade this entire third row softly orange to express recomputation; underneath a single arrow/bracket starting at the first changed block and extending through the end, exact caption “从变化处起，后续状态通常需要重算”.
Keep row spacing generous; brackets cannot intersect text. Do not depict later same text as reusable independently after the changed prefix. No arbitrary numbers, token counts, hit rates, or clocks.
At lower right, include ONE small Xiaotian character, ~25% canvas height, crouching and using a magnifying glass to inspect the changed first block via a short clear guide line. Deep brown tousled hair, dark cat ears, black round glasses, navy hoodie with cream coffee-and-gear emblem, dark grey cargo trousers, natural short neck, wrist device on anatomical left wrist. Preserve identity from reference. Character must not cover chart or text.
Bottom note exactly: “Token 序列示意；实际命中还取决于服务端缓存”
Do not include any other text, watermarks, logos, extra characters, grids, tables, or dense paragraphs. Careful technical alignment and typography are essential.
```

### 图 1：局部修订提示词

```text
Edit this educational diagram only to correct the meaning of the third request row. Preserve all layout, text, character identity, proportions, paper texture, and rows one and two. In row 请求三, recolor ALL FIVE individual token blocks (规则 B, 工具, 问题, 历史, 新结果) to the SAME muted pale orange fill and dark orange outline. None of the blocks in the third row may remain teal/blue. This indicates that after the first changed token, all subsequent cached states generally need recomputation even when later text matches. Preserve the orange arrow running beneath the entire third row and all exact Chinese captions. Do not add any content or change any text. Keep landscape 16:9, full-resolution crisp readable text.
```

## 图 2：生成提示词

```text
Create a new original 16:9 landscape Chinese technical article illustration explaining progressive disclosure of Agent Skills. Use the attached image ONLY as the visual identity reference for Xiaotian, not as a layout reference. Warm ivory subtle paper, fine hand-drawn dark ink outlines, muted teal and warm orange accents, generous negative space, large perfectly readable Chinese labels. No page title, no watermark, no extra invented text.
Depict a left-to-right causal sequence using three visually different physical objects connected by clear small arrows, not three equal dashboard cards.
LEFT: a slim two-entry catalog sheet, heading exactly “先看目录”, entries exactly “文档取证” and “回答检查”. Highlight only 文档取证 with orange. Small caption under sheet exactly “名称与适用场景”.
CENTER, largest: an opened handbook labeled “再读选中的指南”, its visible text exactly “定位文档 → 读取正文 → 核对依据”. A separate unopened slim book beside it is labeled “回答检查”, rendered gray, with small caption “暂不加载”. The catalog's selected entry points to this opened handbook, not to the unopened book.
RIGHT: a small partially opened resource drawer with two visible file tabs labeled “参考文件” and “脚本”. Heading above exactly “需要时再取资源”. Arrow from the opened handbook to the drawer is dashed to indicate optional, on-demand loading. No arrow from the unopened book.
One Xiaotian character stands between center and right and is actively selecting a reference file from the drawer while holding a page from the opened handbook. Young adult chibi, approximately three heads tall, short natural neck, messy deep-brown hair, dark cat ears, round black glasses, brown eyes, navy hoodie with cream coffee-and-gear emblem, gray cargo pants and gray low shoes, device on anatomical left wrist. Exactly two arms and two hands; character does not obscure any labels.
Across the bottom, a small footnote exactly “理论分为三级；本文 Java 实验实现目录与指南正文两级”.
Meaning must be unmistakable: model first receives brief discovery metadata, then one task-relevant instruction body, then extra resources only if needed; it does not read everything upfront. Draw resources as optional future theory, not claimed implementation. Output one polished readable educational illustration.
```

## 图 3：生成提示词

```text
Create one new original 16:9 landscape Chinese educational illustration for a Java Agent context engineering article: compress completed tool history while keeping evidence and the newest complete tool batch. Use the attached Xiaotian character image only for identity. Warm ivory fine paper, precise hand-drawn dark ink, muted teal for retained data, warm orange for consolidation, large legible Chinese words, airy composition. This is a mechanism diagram with a character taking part, not a poster of equal cards. No title, no watermark, no invented text.
At the very TOP, a slim continuous unchanged band reads exactly “原始问题与约束：始终保留”.
LEFT side: show three paper pairs in a row under the label “较早的完整批次”. Each pair has a top smaller sheet labeled “调用” attached by a line to a bottom sheet labeled “结果”. Repeat content represented by matching simple line patterns. A clear orange merging arrow from all THREE older pairs leads toward the CENTER.
CENTER: one modest consolidated dossier labeled exactly “去重后的原始观察”. Its four prominent tab labels are “来源”, “事实”, “未知项”, “失败”. The dossier is shown preserving records, with no eraser, no trash bin, no misleading deletion of failures. A small caption beneath reads exactly “工具名 + 参数 + 原始结果”. One small Xiaotian character carefully gathers duplicate papers into this dossier, actively checking a page with a pencil. Exactly two arms, natural short neck, no obscured text.
RIGHT: a visibly intact latest pair of paper cards enclosed in ONE teal bracket labeled “最近一个完整批次”. Top card text “工具调用”; bottom card text “全部对应结果”; a short connecting line clearly links the pair. These are not merged into the center dossier. A plus sign between the dossier and latest pair indicates BOTH are sent onward. Under the pair add exactly “保持调用与结果配对”.
At the very bottom place a thin light gray archive strip with a few tiny paper icons and the note exactly “完整旧批次仍在本次 Java 对象中；尚未持久化”.
The causal story is old completed batches→deduplicated verbatim observations; keep latest complete call/results; retain original question and constraints; retain source/known facts/unknowns/errors. This is NOT a model-generated semantic summary, and not restart recovery. Avoid showing independent old tool messages being sent without their original call. Match character identity: deep-brown hair, dark cat ears, round black glasses, navy hoodie cream coffee-and-gear emblem, gray cargo pants, gray low shoes, device anatomical left wrist. Ensure all Chinese text reads exactly as provided.
```
