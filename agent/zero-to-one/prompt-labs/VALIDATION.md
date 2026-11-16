# 提示词实验工程验证记录

本文件记录编译、确定性计算和客户端协议检查，不评价线上提示词效果。学习步骤见 [README](README.md)，评审意见的处理见[审阅记录索引](../reviews/INDEX.md)。

## 如何复跑

在 prompt-labs 目录用 JDK 21 执行固定工程检查：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' SamplingLab.java --self-check
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' EvidenceCheckLab.java --self-check
```

交互探索与自检分开。无参数只显示问题；读者通过参数改变实验条件，不需要修改自检预期。

在 weather-agent 模块目录编译主代码：

```powershell
mvn -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { throw '主代码编译失败。' }
```

协议检查位于 test 源码目录，**需要另外编译，并把 test-classes 加入类路径**：

```powershell
mvn -q -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/prompt-lab-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw '检查程序编译或类路径生成失败。' }
$labCheckClasspath = 'target/test-classes;target/classes;' + (Get-Content -LiteralPath 'target/prompt-lab-classpath.txt' -Raw).Trim()
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $labCheckClasspath example.weather.lab.DeepSeekPromptLabProtocolCheck
if ($LASTEXITCODE -ne 0) { throw '本机协议检查失败。' }
```

它是独立 main，不是 JUnit 测试；Maven test-compile 仅编译，不能代替最后一条实际执行。使用 JDK 回环 HTTP 替身，响应由检查程序按顺序提供，不根据提示词模拟模型智力。

## 2026-11-07：本轮评审修复验证

环境：Windows、显式 JDK 21、Maven 3.9.10。Maven 实际使用 `-o` 读取本机缓存，没有下载依赖。检查过程中未配置 DeepSeek Key，未访问线上模型或高德。

| 检查 | 实际结果 | 能证明的范围 |
|---|---|---|
| 两个离线程序 `--self-check` | 10 + 13 项通过 | 固定数值与字段核验回归 |
| 离线命令行场景 | 29 项通过 | 默认不揭晓、参数变化、边界拒绝、两类盲区及预测要求 |
| Maven 离线 compile / test-compile | 通过 | 当前主代码与检查程序编译 |
| 本机协议检查 | 99 项运行时累计断言通过 | 工具协议、隔离、预算、JSON、错误处理与可读对照 |
| JDK 21 `javac --release 21 -Xlint:all -Werror` | 通过 | 两个离线程序及线上客户端、协议检查的严格编译 |
| 四份 Java 的 `javadoc -private -Xdoclint:all,-missing` | 通过 | 注释标记与文档格式；不表示自动判断解释质量 |
| 线上入口 `--help` | 通过 | 不读取凭据、不发送请求的使用说明 |
| 修改文档的本地路径与锚点 | 104 处通过 | 文件引用、Markdown 锚点、九张图号及折叠块配对 |

实际输出：

```text
PASS: 10 项数值与边界检查；不代表任何模型的准确率。
PASS: 13 个合成案例检查；不是模型评估成绩。
PASS: 29 项离线 CLI 场景；JDK 21 严格编译通过。
PASS: 99 项本机协议与边界断言；未调用线上模型。
```

本轮协议检查在原 85 项基础上增加 14 项，覆盖 chat 系统规则传出、预测保留、选项边界、凭据隔离、默认留出情形、文本与 JSON 比较，以及中断后的真实分母。断言数是本次进程累计值，不是静态案例数、学习成果或模型成绩。

离线 CLI 验证还实跑了 top-p=0.95、分数换位、全零分数、日期与时区同时错误、错误地点证据、无证据和两类漏检。合法探索不再触发默认夹具的旧断言。29 项使用临时构建目录中的 class，由命令行逐项核对退出码和关键输出；没有引入新的测试依赖。

## 历史记录

以下两段从修订前 README 移入，保留当时的数量和未覆盖范围，不把它们改写成本轮结果。

2026-11-03，使用 JDK 21 完成主代码与检查程序编译，本机协议检查的 46 项断言通过，覆盖消息回填、调用 ID、五类用例、字段核验、重复请求拒绝、非法工具与参数、截断回答、模型列表、HTTP 401/429/500 不重试和凭据隔离。本次进程未配置 `DEEPSEEK_API_KEY`，未执行线上集成或三种提示词的真实模型效果对照。

2026-11-07，完成实验入口的职责拆分后，Maven 离线主代码编译、检查程序编译、JDK 21 严格编译与 Javadoc 格式检查通过，实际执行的本机协议断言增至 85 项。新增覆盖三种提示词的任务隔离、限流后停止比较、整批预检拒绝、工具预算、模型未列出、重复 JSON 键及 Unicode 转义的凭据回显。帮助入口已运行；这些验证只使用本机 HTTP 替身，未执行线上 DeepSeek 集成或真实模型质量比较。

## 未覆盖的验证

未执行线上 `check/chat/tools/compare`，没有故障任务的真实模型结果快照。可读对照生成与判读由本机替身覆盖，但提示词是否改善实际模型行为，须配置本机 Key 后用真实报告观察；不预设一定出现差异。天气历史回放可以帮助认识执行流程，不能替代固定故障任务的对照。
