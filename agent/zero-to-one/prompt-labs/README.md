# 提示词实验：先预测，再用记录修正解释

正文 [第 03 篇](../03-prompt-engineering.md)讲原理与实验设计，本讲义负责运行、观察和排错；编译与工程检查记录放在 [VALIDATION.md](VALIDATION.md)。需要 JDK 21，两个离线程序无额外依赖。

| 实验 | 要回答的问题 | 条件 | 不覆盖 |
|---|---|---|---|
| 3-1 | 改温度和 top-p，会改变谁的选择机会？ | 离线、无 Key | 不调用模型、不抽样、不测质量 |
| 3-2 | 字段核验能发现哪些错误，又会漏掉什么？ | 离线、无 Key | 不证明来源真实、不理解自然语言 |
| 3-3 | 同一证据下，提示词变化是否伴随行为或答案变化？ | DeepSeek Key、Maven | 不评价任意生产摘要、不自动证明因果 |

编号按第 03 篇中实验首次出现的顺序排列：2.4 节为 3-1，4.4 节为 3-2，5.3 节为 3-3；第一位是篇号。

## 零 Key 的阅读与操作路径

先打开[天气任务离线回放](../prompt-demos/three-model-calls/index.html)：逐格找出 system、user、assistant、tool 的来源，检查信息如何增加。它是一次历史真实运行，不是三种提示词的效果对照。再完成下面两个离线实验，最后阅读 3-3 的执行流程和固定契约；没有 Key 时，将“线上表现”标为未验证。

复制这张空表，每轮先写左侧，再揭晓结果。命令中的预测只是填写示例，请替换为自己的判断。

| 输入与变化因素 | 我的预测 | 实际观察 | 哪条解释需要修正 |
|---|---|---|---|
| 3-1：分数不变，温度 1 → 0.5 | | | |
| 3-1：温度 1，top-p 0.8 → 0.95 | | | |
| 3-2：未知概率改成 0 | | | |
| 3-2：字段一致但附带文字编造 | | | |
| 3-3：not-found，basic → rules → examples | | | |

## 实验 3-1：观察概率，区分选择策略与事实依据

在本目录打开 PowerShell，先查看问题：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' SamplingLab.java
```

默认只显示人工分数和问题。候选 A/B/C 的语义是假设的“编造时间 / 保留未知 / 请求材料”，不是从真实模型取出的 Token。分数同为 `[2,1,0]`，分别预测并运行：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' SamplingLab.java --temperature=0.5 --predict=填写你的预测 --reveal
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' SamplingLab.java --temperature=2 --predict=填写你的预测 --reveal
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' SamplingLab.java --top-p=0.95 --predict=填写你的预测 --reveal
```

一次只改变主要因素：温度实验保持 top-p 不变，重点比较筛选前分布；top-p 实验固定温度，比较筛选后分布。再用 `--logits=0,2,1` 与 `--logits=0,0,0`，检查原解释在最高分换位、并列时是否成立。

| 选项 | 默认值与约束 |
|---|---|
| `--logits` | `2,1,0`；固定三个有限分数，逗号分隔 |
| `--temperature` | `1`；有限正数；贪心选择另由 argmax 展示 |
| `--top-p` | `0.8`；在 (0,1] 内 |
| `--predict`、`--reveal` | 揭晓必须同时记录非空预测；不揭晓时不打印计算结果 |
| `--self-check` | 单独使用，运行固定的 10 项工程回归检查 |

<details>
<summary>运行后核对：默认分数的观察</summary>

T=1 时 A/B/C 约为 0.6652/0.2447/0.0900；T=0.5 时 A 约为 0.8668；T=2 时约为 0.5065。温度为 1、top-p=0.8 时保留 A、B；top-p=0.95 时三项均保留。全部分数相等时，正温度变化不会制造偏好；argmax 并列取原顺序第一项。

程序输出“概率变化”来自本次输入的实际计算，不套用默认结果。降低温度若强化了错误候选，不能称为事实改善。以后修改提示词时，应先补足材料与缺口规则，再决定表达的随机性。

</details>

源码阅读：[SamplingLab.java](SamplingLab.java) 的 `main → parseOptions → softmax / topP / argmax`。共同平移分数与输入拒绝归入 `runSelfChecks`，探索参数不会与旧断言冲突。

## 实验 3-2：发现字段错误，并检查核验边界

[EvidenceCheckLab.java](EvidenceCheckLab.java) 固定目标地点、日期与合成天气证据。先不揭晓，查看输入；随后一次改变一个候选条件：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --probability=0 --predict=填写你的预测 --reveal
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --date-offset=1 --timezone=UTC --predict=填写两个错误的预测 --reveal
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --evidence-location=310000 --location=310000 --predict=字段相同是否足够 --reveal
```

第二条练习比较未知与零；第三条刻意组合两项错误，观察是否累计；第四条把证据和候选一起换到其他地点，观察“彼此一致”与“属于本次任务”的区别。合成日期固定为 2026-10-22，不读取当前日期、不查询真实天气。

| 选项 | 默认值与范围 |
|---|---|
| `--case` | `fields`；另有 `no-evidence / prose-conflict / forged-evidence` |
| `--location`、`--evidence-location` | 均为 `110000`，正整数；分别改变候选地点和证据地点 |
| `--date-offset` | `0`，候选日期相对固定目标的整数天偏移 |
| `--timezone` | `Asia/Shanghai`，有效 IANA 时区 |
| `--source` | `高德地图`，可传 `null` 表示候选遗漏来源 |
| `--probability` | `null`，或 0～100 的整数 |
| `--outcome` | `ANSWER`，也可为 `UNAVAILABLE`；后者不附带候选事实 |
| `--predict / --reveal` | 预测与揭晓规则同 3-1 |
| `--self-check` | 单独使用，运行 13 个固定回归案例 |

地点、日期、时区、来源、概率六项字段选项仅适用于 `fields`。证据缺失的处理可分别用 `--case=no-evidence` 和 `--case=no-evidence --outcome=UNAVAILABLE` 对照。

再做两个盲区实验，先判断核验会不会拒绝：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --case=prose-conflict --predict=填写你的预测 --reveal
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --case=forged-evidence --predict=填写你的预测 --reveal
```

<details>
<summary>运行后核对：错误码与漏检原因</summary>

`--probability=0` 返回 `[PROBABILITY]`；日期和时区同时错返回 `[DATE, TIMEZONE]`。证据地点不属于目标时，即使候选与证据相同，也先返回 `[EVIDENCE_MISMATCH]`；证据日期不符或来源为空也走此分支。

两个盲区场景均返回 `[]`。前者的自然语言根本没传入 verify；后者把证据和候选共同伪造，字段相等无法证明来源真实。verify 的字段契约没有失效，失效的是“字段通过便能证明整份回答可靠”的推论。接真实系统时，还需对照完整回答并验证受控采集轨迹。

`runSelfChecks` 包含 6 个候选字段案例、4 个证据适用性案例、2 个完整性案例和 1 个概率越界案例。“错误候选被拒绝”是一项正确程序行为，不能把 13 项通过换成模型准确率。

</details>

阅读顺序：探索入口 `main` → 纯函数 `verify` → 固定回归组 `runSelfChecks`。以后设计核验时，应明确输入是否覆盖最终交付内容、证据由谁取得，再说明空错误列表能证明什么。

## 实验 3-3：线上 DeepSeek 调用与对照

入口：[DeepSeekPromptLab.java](../weather-agent/src/main/java/example/weather/lab/DeepSeekPromptLab.java)。在 IDEA 导入天气模块 pom，SDK 选择 JDK 21，Application 主类为 `example.weather.lab.DeepSeekPromptLab`，工作目录设为 `agent/zero-to-one/weather-agent`。不启动 Spring、不加载 YAML、不需要高德 Key。

| 环境变量 | 用途 |
|---|---|
| `DEEPSEEK_API_KEY` | 当前进程的凭据，保存在本机环境或运行配置，不写进参数、源码或报告 |
| `DEEPSEEK_MODEL` | 可选；源码默认 `deepseek-flash`，以当前账户模型列表为准 |
| `DEEPSEEK_BASE_URL` | 可选；默认官方 HTTPS 地址，也接受官方 /v1 基础路径 |

PowerShell 用户在 weather-agent 目录编译并生成类路径；IDEA 用户可把下面选项填入 Program arguments：

```powershell
mvn -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/prompt-lab-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw '编译或类路径生成失败。' }
$labClasspath = 'target/classes;' + (Get-Content -LiteralPath 'target/prompt-lab-classpath.txt' -Raw).Trim()
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $labClasspath example.weather.lab.DeepSeekPromptLab --mode=check
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $labClasspath example.weather.lab.DeepSeekPromptLab --mode=chat --predict=填写你的预测
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $labClasspath example.weather.lab.DeepSeekPromptLab --mode=tools --case=unknown --prompt=rules --predict=填写你的预测
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $labClasspath example.weather.lab.DeepSeekPromptLab --mode=compare --case=not-found --repeats=1 --predict=填写你的预测
```

先判读当前阶段再运行下一条，避免将连接失败带到后续实验。`check` 只请求模型列表，其余命令均调用线上生成并可能产生用量。`chat` 可以增加 `'--system=只用两项列表回答。'`，与默认规则比较表达要求；用户问题保持不变。`--system` 只适用于 chat，最长 2000 字符。`--predict` 适用于 chat/tools/compare，最长 1000 字符，用来记录预测，不发送给模型。

### 先核对轨迹，再看结果

阅读路径为 `main → runExperiment → run → runConversation`；任务状态放在每项自己的 `TaskState`。重点检查：

1. `rounds[0].request.messages` 的 system/user 内容；工具定义在 `request.tools`，不是一条 tool 消息。
2. 模型提出 `tool_calls` 后，`executeToolBatch → preflight` 整批校验；任一项非法则整批不执行。
3. `toolEvents` 仅记录实际执行；下一轮回填原 assistant 消息和匹配 `tool_call_id` 的 tool 消息。
4. `answer` 保留原始候选。`verify` 分别检查工具依据、JSON 与字段，不替模型去围栏、补字段或修复未知值。

用例为 `complete / unknown / missing-id / not-found / failure`，固定契约见[正文 5.3.1](../03-prompt-engineering.md#531-固定什么改变什么)。tools 默认 unknown/rules；compare 默认 not-found，固定 basic/rules/examples 顺序，`--repeats` 仅适用于 compare，限 1～3。`--prompt` 仅适用于 tools。

每项最多 4 次模型请求、3 次工具执行，温度 0、max_tokens 1024、thinking disabled、单请求超时 45 秒、不重试。一次三方案比较最多 12 次生成请求，三次重复最多 36 次。API Key 只用于请求头；非 200 的远端错误正文不入报告。

### 用对照表修正自己的解释

每次在 `target/prompt-tool-runs/run-<UUID>/report.json` 保存实际任务。compare 另生成 `comparison.md`，控制台也打印实际候选与对照：

- 同一行列出累计提示词变化、模型请求/工具执行数、状态、停止原因、错误项。
- 五个字段按 basic/rules/examples 三列并排显示；无法解析和未运行分别标记。
- 每个方案附运行前预测和原始候选，与本重复的 basic 分别比较文本相等、JSON 结构相等。
- 通过数的分母只取已运行任务；0/0 表示未运行，不是零分。

`CANDIDATE` 表示取得候选；tools 的 passed 还要求本次工具依据与固定字段通过。`check` 的 CHECKED 只表示列表合法，模型未列出仍不通过；chat 的通过只表示正常结束的非空文本。工具返回 UNAVAILABLE 后如实表达缺口可以通过，不能与 DeepSeek 请求失败混为一谈。

三项都通过就记录“本样本未观察到契约通过情况的差异”，继续阅读实际答案和工具次数。先保留所有成功与失败，再决定下一步。可固定 unknown/rules，只移除一条 null 规则并重新编译后对照；确认实际 request 的 system 已变化，恢复源码后也重新编译。还要用未在示例中出现的 not-found/failure 检查退化。

比较结果不能直接归因于单条规则；顺序固定、服务更新、网络与缓存可能影响用量或耗时。以后改提示词时，先确定失败类型与评价契约，再保留对应输入和实际轨迹，而不是只记录“回答更好了”。

### 失败时怎样定位

下面是常见码的分类；具体触发条件见源码的 `LabFailure`、`evaluateRun` 和 `verify`，代码没有错误码 enum。

| 类别与错误码 | 先检查什么 |
|---|---|
| `MODEL_NOT_LISTED / INVALID_MODEL_LIST` | 模型可见性，响应 data 是否为数组 |
| `HTTP_*`（如 401/402/429）、`NETWORK_ERROR / TIMEOUT / INTERRUPTED` | 凭据、账户、网络、限流或进程中断；服务失败停止比较 |
| `REJECTED_RESPONSE / INVALID_RESPONSE_JSON / INVALID_ASSISTANT_MESSAGE` | 响应大小、凭据回显、JSON 或 assistant 消息协议 |
| `UNEXPECTED_TOOL_CALLS / INVALID_TOOL_CALL / INVALID_TOOL_ARGUMENTS` | 当前阶段、工具名、调用 ID、参数类型与白名单 |
| `DUPLICATE_QUERY / TOOL_BUDGET / MODEL_CALL_LIMIT` | 重复查询、工具和模型预算；最后一轮不执行无法继续回填的工具 |
| `INCOMPLETE_ANSWER` | 空文本、截断或异常结束原因 |
| `TOOL_EVIDENCE / ANSWER_JSON / ANSWER_FIELDS` | 行为依据、候选格式、固定字段 |

选项错误在请求前拒绝。离线程序的非法温度、NaN、越界概率等也会非零退出，应修正输入；不要把输入拒绝当模型质量下降。工程回归断言失败则按 [VALIDATION.md](VALIDATION.md) 重跑并定位。

本次修订未配置 Key，未运行线上 DeepSeek；没有真实对照快照。零 Key 路线和替身检查均已明确标注，不能用于推断线上提示词效果。
