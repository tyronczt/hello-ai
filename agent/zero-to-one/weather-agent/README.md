# weather-agent：提示词与上下文的真实天气实践

本项目对应[第 03 篇提示词工程](../03-prompt-engineering.md)与[第 04 篇上下文工程](../04-context-engineering.md)。城市和天气来自真实 Open-Meteo；模型提出工具请求，Java 执行并把实际结果交回模型。它是独立命令行项目，第 02 篇的 `first-agent` 保留原实现。

## 环境与外部服务

沿用本系列已有技术组合：JDK 21、Spring Boot 4.1.1、Spring AI 2.0.1，使用 Maven 构建。这是项目锁定版本，不是最新版本推荐。天气 HTTP 使用 JDK `HttpClient`，没有新增天气 SDK。

Open-Meteo 的非商业免费接口无需注册或 API Key，有调用限制且不提供免费接口的可用性保证；商业用途需另核对服务方案。数据署名保留 Open-Meteo，地名来源保留 GeoNames。[服务条件](https://open-meteo.com/en/pricing) · [数据许可](https://open-meteo.com/en/licence)

模型服务沿用 DeepSeek OpenAI 兼容配置，通过 `DEEPSEEK_API_KEY` 设置密钥；可通过 `DEEPSEEK_BASE_URL` 和 `DEEPSEEK_MODEL` 显式调整模型服务。当前默认模型名沿用现有项目的 `deepseek-flash`，其他服务与模型没有验证。

## 先检查真实天气接口

在本模块目录执行；`java` 应指向 JDK 21，`mvn` 应在 PATH 中：

```powershell
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw '打包失败。' }
java '-Dfile.encoding=UTF-8' -jar target/weather-agent-0.0.1-SNAPSHOT.jar --weather-check
if ($LASTEXITCODE -ne 0) { throw '天气接口检查失败，请查看生成的报告。' }
```

该命令只查询公开北京地点和天气，不创建模型客户端，也不需要模型密钥。它先搜索北京，再使用已经核验的北京候选 ID `1816670` 检查天气接口。普通 Agent 入口根据用户要求选择候选，不默认使用这个固定 ID。

成功时打印 `COMPLETED` 和本次 `report.json` 的绝对路径。失败时保存错误记录并以非零状态结束，不返回教学天气作为兜底。无参数启动只打印用法，不调用外部服务。

## 运行一次模型与天气任务

先通过运行环境配置 `DEEPSEEK_API_KEY`，再执行：

```powershell
java '-Dfile.encoding=UTF-8' -jar target/weather-agent-0.0.1-SNAPSHOT.jar '--ask=北京市明天的天气怎样？请说明降水概率，并给出带伞建议、目标日期和来源。' --prompt=rules --context=full
```

这会调用模型服务并产生模型用量，也会按模型选择请求 Open-Meteo。报告会保存本次问题、逐轮消息和公开工具返回，示例运行应使用公开教学问题。

IDEA 中打开本模块 `pom.xml`，选择 JDK 21，主类为 `example.weather.WeatherApplication`，工作目录为本模块。把相同参数填入 Program arguments。程序不启动 HTTP 服务。

## 每次只改变一个因素

| 参数 | 值 | 作用 |
|---|---|---|
| `--prompt` | `basic` | 基本任务与共同事实边界 |
| `--prompt` | `rules` | 增加处理流程、完成条件和输出组织；默认 |
| `--prompt` | `examples` | 在 rules 上增加缺失信息、服务失败、null 值的示例 |
| `--context` | `full` | 保留当前任务的完整工具历史；默认 |
| `--context` | `state` | 完整历史加一个最新状态快照 |
| `--context` | `compact` | 状态快照加旧批次整理；达到阈值且确实更短才采用 |

研究提示词时固定 `--context=full`；研究上下文时固定 `--prompt=rules`。没有自动批量调用线上模型的入口。真实天气会变化，逐次运行并非严格固定天气数据的对照。

[提示词工程章节](../03-prompt-engineering.md)进一步说明任务契约、示例学习、工具描述、采样参数和消融评估，并提供两个独立的 [JDK 实验](../prompt-labs/README.md)。离线实验不访问天气或模型服务，也没有接入本项目的答案处理；当前自然语言候选仍需结合工具证据核对。

## 工具与执行边界

`searchLocations(city)` 返回最多五个候选，包含实际 ID、行政区、国家、坐标与时区。`getWeather(locationId)` 只接受本次已经检索到的 ID，返回当前条件与包含今天的三天预报。缺失地点需要追问，超出返回日期范围的预报不能编造。

当前条件来自气象模型数据，不等同于现场测量。温度、概率、有效时间与单位均保留服务返回；`retrievedAt` 只表示本次获取时间。null 保持未知，数组错位按无效响应处理。完整字段解释见 [Open-Meteo 文档](https://open-meteo.com/en/docs)。

每项任务最多 8 次模型调用、12 个工具请求；同一批次先整体校验，最后一轮不再执行无法回填给模型的工具。天气连接超时为 5 秒，请求超时为 15 秒，不自动重试；模型请求超时配置为 45 秒。五分钟任务预算在阶段之间检查，不能中断已经阻塞的外部请求。

`COMPLETED` 表示模型产生非空候选回答，可能是答案或追问，不等于自动核验通过。每次运行独立创建地点集合和消息，没有跨任务记忆，没有后台任务与业务写操作。

## 报告怎样读取

每次报告保存到独立目录 `target/weather-runs/run-*/report.json`，以终端打印的路径为准。

| 字段 | 含义 |
|---|---|
| `kind` | `LIVE_WEATHER_TOOL_CHECK` 为真实工具检查；`MODEL_RUN` 为配置模型任务，是否查询天气看工具记录 |
| `tools` | Agent 模式注册的工具说明与参数 Schema |
| `rounds[].messages` | 调用前的 Spring AI 消息视图，不是最终 HTTP 抓包 |
| `rounds[].messageJsonUtf16Units` | 消息视图 JSON 的 UTF-16 长度，包含字段名与转义，不是 Token 数 |
| `rounds[].compacted` | 本轮是否实际采用压缩视图 |
| `rounds[].usage` | SDK 取得的模型用量；没有原生用量时为 null |
| `toolEvents` | 实际工具参数、结果、来源与获取时间 |
| `status`、`answer` | 候选回答或停止原因 |

## 已验证的范围

2026-10-19 在 JDK 21 下完成 `mvn -q -DskipTests compile` 与 `mvn -q -DskipTests package`。本机临时检查通过 41 项断言，包括参数与地点隔离、服务错误、未知值、整批工具预检、调用预算、消息配对、压缩信息保留，以及真实 Spring AI 适配器的本机 HTTP 工具循环。打包后 Spring 入口还核对了指定模型、输出上限与思考模式配置的实际传递，无参数启动和非法模式拒绝。

这些检查使用明确标记的天气和模型替身；不是 Maven 自动发现的 JUnit 测试。临时检查代码放在按项目惯例忽略的 `.local-checks/WeatherChecks.java`，结果在 `target/checks/summary.json`，本机协议请求在 `target/checks/wire-requests.json`。

打包后的 Java 程序另行运行 `--weather-check`，真实 Open-Meteo 城市检索和天气查询成功。真实模型回答质量、不同网络环境的稳定性、模型 Token 节省和服务端缓存命中尚未验证。
