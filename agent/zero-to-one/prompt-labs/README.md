# 提示词工程：基础计算与线上工具调用实验

配合[提示词工程](../03-prompt-engineering.md)阅读。两个单文件基础实验使用 JDK 21，无依赖、无密钥、无网络调用。新增的线上实验直接调用 DeepSeek，复用 `weather-agent` 的已有依赖，不改变天气应用的请求接口。

## 两个离线基础实验

从仓库根目录运行：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' agent/zero-to-one/prompt-labs/SamplingLab.java
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' agent/zero-to-one/prompt-labs/EvidenceCheckLab.java
```

| 实验 | 观察与自检 | 结论边界 |
|---|---|---|
| `SamplingLab` | softmax、温度、top-p、贪心选择；10 个数值与参数边界检查 | logits 为人工指定，只验证计算，不代表真实模型对提示词的反应 |
| `EvidenceCheckLab` | 同一目标和证据下检查地点、日期、时区、来源、null、服务失败；12 个合成案例检查 | 输入已经是 Java record；没有 JSON 解析、自然语言提取、真实工具回放或模型质量评分 |

检查失败时抛出异常并以非零状态退出，不依赖 `-ea`。类中保留自检是为了让读者修改数据后立即观察边界，不是 Maven/JUnit 测试集。

第二个实验中的日期和地点仅用于合成夹具，降水概率未知；不能用作天气预报。它检查证据与候选是否一致，不能验证气象模型本身是否准确，也不评判自然语言建议。它的可用/不可用二分只覆盖预报核验；用户条件缺失时的追问需要另一组任务。

两份程序均只使用局部变量和不可变输入；没有共享可变状态。若接入实际服务，应沿用天气助手的每任务状态隔离，并另行处理解析、单位、时间有效性、网络失败及授权边界。

## 线上 DeepSeek：工具循环与提示词对照

完整步骤见正文第 5.3 节。入口：[DeepSeekPromptLab.java](../weather-agent/src/main/java/example/weather/lab/DeepSeekPromptLab.java)。在 IDEA 中以 JDK 21 运行它的 `main()`，工作目录为 `agent/zero-to-one/weather-agent`，在 Environment variables 配置 `DEEPSEEK_API_KEY`；可选 `DEEPSEEK_MODEL`（默认 `deepseek-flash`）和官方 `DEEPSEEK_BASE_URL`（默认 `https://api.deepseek.com`）。它不加载 Spring 或 YAML，不需要高德 Key。

按顺序在 Program arguments 填入下列选项，分别运行：

| 阶段 | Program arguments | 检查对象 |
|---|---|---|
| 连接 | `--mode=check` | 请求模型列表，核对可访问的模型 |
| 普通回答 | `--mode=chat` | 消息角色、非空候选与结束原因 |
| 工具往返 | `--mode=tools --case=unknown --prompt=rules` | 模型请求、Java 执行、调用 ID 配对和未知值 |
| 提示词对照 | `--mode=compare --case=unknown --repeats=1` | 三种累计方案在同一用例下的行为与字段 |

用例包括 `complete`、`unknown`、`missing-id`、`not-found`、`failure`。工具返回固定合成故障报告；模型在线生成，每次模型请求可能产生用量。`compare` 每次只比较一个用例，重复次数限 1～3，不自动重试。服务鉴权、限流或网络错误停止整次比较。

每项任务最多 4 次模型请求、3 个工具调用；最后一轮不再执行无法回填的工具。程序拒绝未知工具、参数类型错误和重复查询。`CANDIDATE` 只说明有候选；`passed=true` 只表示固定任务的工具依据和字段契约通过。`chat` 模式只检查非空正常结束，不评价解释质量。

报告保存在模块的 `target/prompt-tool-runs/run-<UUID>/report.json`，包含各次 `request`、成功响应（包括服务实际返回的 `usage`）、工具记录、耗时和错误码。请求头与 Key 不入报告，非 200 的远端错误正文不入报告。使用内置教学问题，不在实验中加入生产数据。

## 本机协议检查

[DeepSeekPromptLabProtocolCheck.java](../weather-agent/src/test/java/example/weather/lab/DeepSeekPromptLabProtocolCheck.java)是独立 `main()` 检查程序，使用 JDK 的回环 HTTP 服务，不需要 Key，不调用 DeepSeek。它没有引入 JUnit；Maven 的 `test-compile` 只编译它，实际断言需要单独运行。

在 `agent/zero-to-one/weather-agent` 目录的 PowerShell 中执行：

```powershell
mvn -q -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/prompt-lab-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw '检查程序编译或类路径生成失败。' }
$labCheckClasspath = 'target/test-classes;target/classes;' + (Get-Content -LiteralPath 'target/prompt-lab-classpath.txt' -Raw).Trim()
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $labCheckClasspath example.weather.lab.DeepSeekPromptLabProtocolCheck
if ($LASTEXITCODE -ne 0) { throw '本机协议检查失败。' }
```

2026-11-03，使用 JDK 21 完成主代码与检查程序编译，本机协议检查的 46 项断言通过，覆盖消息回填、调用 ID、五类用例、字段核验、重复请求拒绝、非法工具与参数、截断回答、模型列表、HTTP 401/429/500 不重试和凭据隔离。本次进程未配置 `DEEPSEEK_API_KEY`，未执行线上集成或三种提示词的真实模型效果对照。
