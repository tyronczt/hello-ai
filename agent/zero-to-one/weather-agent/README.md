# weather-agent：通过 HTTP 提问的真实天气助手

本项目对应[第 03 篇提示词工程](../03-prompt-engineering.md)与[第 04 篇上下文工程](../04-context-engineering.md)。城市与预报通过高德 Web 服务查询；模型提出工具请求，Java 执行并把实际结果交回模型。使用方式与 `first-agent` 一致：在 IDEA 启动服务，再通过 HTTP 提交天气问题。启动普通服务时不会自动向模型提问。

`WeatherApplication` 只负责启动 Spring 服务；HTTP 请求通过 Controller 进入业务服务，再由 Agent 组织模型与工具循环。辅助命令使用独立的 `WeatherCli` 主类。每次请求独立运行，详细报告留在本地。

## 环境与外部服务

沿用本系列已有技术组合：JDK 21、Spring Boot 4.1.1、Spring AI 2.0.1，使用 Maven 构建。这是项目锁定版本，不是最新版本推荐。天气 HTTP 使用 JDK `HttpClient`，没有新增天气 SDK。

高德的[行政区域查询](https://lbs.amap.com/api/webservice/guide/api/district)与[天气查询](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)需要 Web 服务类型的 Key。本项目通过 Java 系统属性 `amap.api.key` 读取它，不从环境变量回退读取。工具只取得预报；当前接口不提供降水概率，适配层将该字段保留为 `null`。

模型服务沿用 DeepSeek OpenAI 兼容配置，通过 `DEEPSEEK_API_KEY` 设置密钥；可通过 `DEEPSEEK_BASE_URL` 和 `DEEPSEEK_MODEL` 显式调整模型服务。当前默认模型名沿用现有项目的 `deepseek-flash`，其他服务与模型没有验证。

## 入口与目录

根包 `example.weather` 只保留启动类，其他代码按职责放入子包：

| 路径 | 职责 |
|---|---|
| [WeatherApplication.java](src/main/java/example/weather/WeatherApplication.java) | 启动 Spring HTTP 服务 |
| [web/WeatherController.java](src/main/java/example/weather/web/WeatherController.java) | 接收并校验请求，完成 Query、DTO、VO 转换 |
| [service/WeatherService.java](src/main/java/example/weather/service/WeatherService.java) | 执行一次任务并保存报告 |
| [service/WeatherReportStore.java](src/main/java/example/weather/service/WeatherReportStore.java) | 生成任务目录，将报告写入本地 |
| [agent/WeatherAgent.java](src/main/java/example/weather/agent/WeatherAgent.java) | 组织模型与工具循环，约束执行预算 |
| [agent/WeatherPrompts.java](src/main/java/example/weather/agent/WeatherPrompts.java)、[WeatherContext.java](src/main/java/example/weather/agent/WeatherContext.java) | 选择规则文本，组织每轮消息 |
| [tool/WeatherTools.java](src/main/java/example/weather/tool/WeatherTools.java)、[AmapClient.java](src/main/java/example/weather/tool/AmapClient.java) | 校验工具参数，查询真实地点和天气 |
| [cli/WeatherCli.java](src/main/java/example/weather/cli/WeatherCli.java) | 运行天气接口检查或单次模型任务 |

## 在 IDEA 启动服务

在 IntelliJ IDEA 中打开本模块 [pom.xml](pom.xml)，作为 Maven 项目导入并等待依赖加载。选择 JDK 21，创建主类为 `example.weather.WeatherApplication` 的运行配置：

1. **Working directory** 设为本模块目录 `hello-ai/agent/zero-to-one/weather-agent`，报告按这个工作目录保存。
2. 在 **Environment variables** 中设置模型密钥 `DEEPSEEK_API_KEY`。
3. 在 **VM options** 中填写 `-Damap.api.key=你的Key`，将占位文本替换为自己的高德 Web 服务 Key。它是 JVM 参数，不能放在 Program arguments。
4. **Program arguments 留空**，点击 Run 或 Debug；服务启动后持续监听 `127.0.0.1:8081`，等待用户提交问题。

不要勾选 **Store as project file / Share through VCS**；运行配置可能以明文保存密钥。真实 Key 不应发到对话、模型或报告，也不要提交到版本库。IDEA Terminal 中设置的环境变量不会自动传给工具栏的 Run 配置。`WEATHER_PORT`、`WEATHER_BIND_ADDRESS` 可覆盖端口与绑定地址，调用地址也应相应调整。本示例没有登录、权限隔离、限流或用量配额，开放给其他设备前需补充相应访问控制。

启动阶段不执行天气任务，不主动调用模型或高德。Run 控制台确认服务启动后，再发送下面的请求。

## 用户提交天气问题

可直接打开本模块的 [requests.http](requests.http)，运行其中的请求；请求结构如下：

```http
POST http://127.0.0.1:8081/api/weather/ask
Content-Type: application/json

{
  "question": "北京市明天的天气怎样？请说明降水概率，并给出带伞建议、目标日期和来源。",
  "promptMode": "rules",
  "contextMode": "full"
}
```

| 字段 | 要求 |
|---|---|
| `question` | 必填，不能是空白；最多 1000 个 UTF-16 编码单元 |
| `promptMode` | `basic`、`rules`、`examples`；省略时为 `rules` |
| `contextMode` | `full`、`state`、`compact`；省略时为 `full` |

模式值按表中的小写文本传入，不自动修剪空格或转换大小写。显式传入 `null`、空字符串或其他模式均无效，不会回退到默认值。

PowerShell 也可发送同样的请求。下面将 JSON 显式编码为 UTF-8 字节，以兼容中文正文：

```powershell
$body = @{
    question = '北京市明天的天气怎样？请说明降水概率，并给出带伞建议、目标日期和来源。'
    promptMode = 'rules'
    contextMode = 'full'
} | ConvertTo-Json -Compress
$response = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/weather/ask' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$response | Format-List taskId, status, answer
```

每次 POST 会同步执行一次任务，再返回以下三个字段。这里仅展示结构，编号和答案为占位文本：

```json
{
  "taskId": "run-...",
  "status": "COMPLETED",
  "answer": "本次模型产生的候选回答"
}
```

先看 `status`：`COMPLETED` 表示模型产生非空候选，可能是答案或追问，不等于事实核验通过；`STOPPED` 表示任务中途停止，`answer` 中保存停止原因。两种状态都可能返回 HTTP 200，所以不能只看 HTTP 状态码。

请求体缺失、JSON 错误或字段不合法返回 HTTP 400；不支持的方法返回 405；非 `application/json` 请求返回 415。此类请求在进入业务处理前被拒绝，没有本次任务摘要。未预期的处理异常返回 500。错误响应使用 `code`、`message` 两个字段。

调用会产生真实模型用量，并按模型选择请求高德。报告保存本次问题、逐轮消息和公开工具返回，跟做时使用公开教学问题即可。HTTP 响应不返回报告正文或本机绝对路径。

## 在 IDEA 中跟踪执行

以 Debug 启动服务，按以下顺序设置断点，再发送一次请求：

1. `web.WeatherController.ask`：检查 `WeatherAskQuery`，查看 Query → DTO 的转换。
2. `service.WeatherService.process`：进入一次业务任务，结束后观察报告保存和任务摘要。
3. `agent.WeatherAgent.run` 中的 `model.call`：检查本轮消息、提示词和工具配置。
4. `tool.WeatherTools.searchLocations`、`tool.WeatherTools.getWeather`：检查工具参数、地点校验及真实返回。
5. `agent.WeatherContext.afterTools`：检查工具请求、结果与下一轮消息的对应关系。

正常天气查询中，模型调用、工具执行与消息回填会交替出现；追问或停止时不一定进入所有工具断点。具体顺序以本次模型响应和报告为准。

Run/Debug 控制台默认以 `INFO` 显示本项目的执行阶段，每行包含 `requestId`。这个编号由服务为每次 HTTP 请求生成，从参数校验前一直传递到同步响应结束；在控制台搜索同一个 `requestId`，可以跟踪本次请求。`report_saved` 和 `task_end` 日志同时包含响应中的 `taskId`，可据此打开本次报告。请求在进入业务处理前被拒绝时，只有 `requestId`，没有任务报告或 `taskId`。

| 日志中的 `stage` | 对应代码与观察内容 |
|---|---|
| `http_request`、`controller_input` | 请求开始、字段校验通过；记录问题的 UTF-16 长度及已校验模式 |
| `task_start`、`agent_start` | 进入 Service 和 Agent，确认模式及模型、工具调用上限 |
| `model_call`、`model_return`、`model_response` | 当前轮次、消息数量与 JSON 长度、剩余预算、模型耗时、工具请求数及候选文本长度 |
| `tool_precheck`、`tool_batch_start`、`tool_batch_end` | 整批预检通过后记录工具名称、批次大小、累计计数及执行耗时；预检失败不会执行该批次 |
| `tool_start`、`tool_end` | 实际工具开始和结束；城市名只记录长度，地点 ID 为数字；结果记录 `OK` 或 `ERROR`、错误码及耗时 |
| `provider_start`、`provider_end` | 实际访问高德的固定域名、HTTP 状态或失败类型、耗时；工具参数被拒绝时不会进入网络请求 |
| `context_ready` | 工具结果已配对并回填，记录上下文模式、已完成批次、下一轮消息数及实际压缩标记 |
| `agent_end` | 候选产生或停止；包含 `status`、固定原因码 `reason`、实际调用计数和 Agent 耗时 |
| `task_report`、`report_saved`、`task_end`、`dto_to_vo`、`http_response` | 保存报告、生成响应摘要并结束请求；可关联 `taskId`、报告相对路径和 HTTP 状态 |
| `http_rejected`、`agent_failed`、`report_failed`、`http_failed` | 校验、协议、模型、工具或报告保存失败；Agent 异常还记录 `phase`、轮次及阶段耗时 |

出现 `model_call` 后还没有 `model_return`，表示这次模型调用尚未返回；出现 `provider_start` 后还没有 `provider_end`，表示天气 HTTP 请求尚未结束。先按 `requestId` 排除其他并发请求，再结合超时配置与异常记录定位。`agent_end` 中的 `EMPTY_ANSWER` 表示模型返回空文本，`INSUFFICIENT_TOOL_BATCH_BUDGET` 表示预算不足以执行整批工具并读取结果，`EXECUTION_FAILURE` 则结合前一条 `agent_failed` 的阶段和异常类型检查。`http_response` 中的 HTTP 200 仍需结合任务 `status` 判断。

需要查看 Query → DTO 转换、模型原生用量、上下文压缩长度比较或异常代码位置时，在同一 IDEA 运行配置的 **Program arguments** 中临时添加以下参数，再重新启动：

```text
--logging.level.example.weather=DEBUG
```

`DEBUG` 仍只输出摘要；异常代码位置只输出栈帧，不输出异常消息或原因正文。问题、提示词、模型答案、原始工具参数和结果请在本地 `report.json` 中查看。日志不打印请求头、模型或高德密钥、完整 URL 查询参数或远端响应正文。消息 JSON 长度以 UTF-16 编码单元计，不是 Token；没有模型原生用量时，`model_usage` 的 `usage` 为 `null`。CLI 不经过 HTTP 请求过滤器，日志中的 `requestId` 为 `none`。

切换请求中的模式不需要重启。修改 Java 源码后，先停止当前 Run/Debug，保存并重新构建项目，再以同一配置启动、发送新请求。检查新报告中的实际消息，确认修改已经生效。

## 每次请求如何隔离

每次 POST 独立创建地点集合、消息和工具计数，不接续上一次请求，也没有跨任务聊天记忆。接口等待本次任务结束后返回；本示例没有后台任务或业务写操作。

## 比较提示词与上下文方案

| HTTP 字段 | 值 | 作用 |
|---|---|---|
| `promptMode` | `basic` | 基本任务与共同事实边界 |
| `promptMode` | `rules` | 增加处理流程、完成条件和输出组织；默认 |
| `promptMode` | `examples` | 在 rules 上增加缺失信息、服务失败、null 值的示例 |
| `contextMode` | `full` | 保留当前任务的完整工具历史；默认 |
| `contextMode` | `state` | 完整历史加一个最新状态快照 |
| `contextMode` | `compact` | 状态快照加旧批次整理；达到阈值且确实更短才采用 |

研究提示词时固定 `contextMode` 为 `full`；研究上下文时固定 `promptMode` 为 `rules`。三种提示词模式用于方案比较，切换模式不等于完成单因素实验。没有自动批量调用线上模型的入口，真实天气也会变化，逐次请求并非严格固定天气数据的对照。

[提示词工程章节](../03-prompt-engineering.md)进一步说明任务契约、示例学习、工具描述、采样参数和消融评估，并提供两个独立的 [JDK 实验](../prompt-labs/README.md)。离线实验不访问天气或模型服务，也没有接入本项目的答案处理；当前自然语言候选仍需结合工具证据核对。

## 报告怎样读取

每次报告保存到 `target/weather-runs/<taskId>/report.json`，路径相对于 IDEA 运行配置中的模块工作目录。HTTP 响应中的 `taskId` 就是本次目录名，可在 IDEA 中打开对应文件。

也可以在本模块目录打开 PowerShell，粘贴本次响应的 `taskId`，不加外层引号：

```powershell
$taskId = Read-Host '粘贴本次响应中的 taskId'
$reportPath = Join-Path -Path 'target/weather-runs' -ChildPath ($taskId + '/report.json')
$report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
$report | Select-Object kind, promptMode, contextMode, status, answer | Format-List
$report.rounds[0].messages | ConvertTo-Json -Depth 20
$report.toolEvents | ConvertTo-Json -Depth 20
```

| 字段 | 含义 |
|---|---|
| `kind` | `LIVE_WEATHER_TOOL_CHECK` 为真实工具检查；`MODEL_RUN` 为配置模型任务，是否查询天气看工具记录 |
| `promptMode`、`contextMode` | 本次选择的提示词和上下文方案 |
| `tools` | Agent 模式注册的工具说明与参数 Schema |
| `rounds[].messages` | 调用前的 Spring AI 消息视图，不是最终 HTTP 抓包 |
| `rounds[].messageJsonUtf16Units` | 消息视图 JSON 的 UTF-16 长度，包含字段名与转义，不是 Token 数 |
| `rounds[].compacted` | 本轮是否实际采用压缩视图 |
| `rounds[].usage` | SDK 取得的模型用量；没有原生用量时为 null |
| `toolEvents` | 实际工具参数、结果、来源与获取时间 |
| `status`、`answer` | 候选回答或停止原因 |

## 工具与执行边界

`searchLocations(city)` 接收城市名称字符串，检索中国省、市或区县候选，不支持国家或街道级查询。统一结果中的 `locations[]` 包含 `id`、`name`、`level`、`adcode`、`country`、`country_code`、`timezone`；不返回坐标。`id` 是 `long` 类型的行政区编码，如北京为 `110000`，仍须使用本次搜索返回的实际值。

`getWeather(locationId)` 只接受本次已经登记的 ID，用 `extensions=all` 获取服务实际返回的预报日期，不查询实况，也不将返回天数固定为三天。缺失地点需要追问，超出返回日期范围的预报不能编造。

以下是 `WeatherTools` 整理后的工具字段，不是高德原始 JSON：

| 字段 | 本项目含义 |
|---|---|
| `source`、`sourceUrl` | 来源为高德地图；来源 URL 不包含 Key |
| `location` | 本次已登记的行政区信息 |
| `timezone`、`localTime` | 适配层使用 `Asia/Shanghai`；`localTime` 是应用查询时钟对应的当地时间，带时区偏移 |
| `reportTime`、`retrievedAt` | 高德预报发布时间、本次获取时刻；不能代替预报日期 |
| `daily[].date` | 每条预报对应的日期；先按 `localTime` 理解相对日期，再匹配此字段 |
| `daily[].dayWeather`、`nightWeather` | 保留高德返回的白天、夜间天气文字 |
| `daily[].dayTemperature`、`nightTemperature` | 白天、夜间温度，不能改称日最高、最低温度 |
| `daily[].precipitationProbability` | 当前接口不提供该指标，固定保留为 `null`，不从湿度或天气文字推算 |
| `units` | 适配层约定温度为 `°C`、概率为 `%`；概率单位不表示已有概率值 |

天气文字的含义见[高德天气现象表](https://lbs.amap.com/api/webservice/guide/tools/weather-code/)；它列出“晴”“多云”等文字，不是旧版 WMO 数值映射。适配层保留原文，不自造数值编码，也不从文字推算降水概率。

原始服务字段可查阅[高德天气查询文档](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)，转换规则见 [WeatherTools.java](src/main/java/example/weather/tool/WeatherTools.java)，HTTP 请求与服务状态校验见 [AmapClient.java](src/main/java/example/weather/tool/AmapClient.java)。回答需要保留未知值，并区分预报、查询时刻与服务发布时间。

每项任务最多 8 次模型调用、12 个工具请求；同一批次先整体校验，最后一轮不再执行无法回填给模型的工具。天气连接超时为 20 秒，请求超时为 30 秒，不自动重试；模型请求超时配置为 45 秒。五分钟任务预算在阶段之间检查，不能中断已经阻塞的外部请求。

如果工具结果为 `MISSING_API_KEY`，先检查当前 Run 配置的 VM options 是否设置了 `-Damap.api.key=...`，而不是 Program arguments 或环境变量；`AUTH_ERROR` 表示高德拒绝了 Key，应核对所用 Web 服务 Key。

如果回答说明天气服务不可用，先查看报告中对应工具的 `result`。`PROVIDER_UNAVAILABLE` 表示网络请求发生 I/O 异常；IDEA 控制台会记录固定服务域名、异常类型和耗时，例如 `HttpConnectTimeoutException`。它不等同于天气供应商已确认停机。`COMPLETED` 仍只表示产生了非空候选，候选可能是在说明本次未取得天气信息。修改超时源码后，停止并重新启动 IDEA 中的服务，再重新发送请求。

## 可选：命令行辅助入口

辅助命令使用独立主类 `example.weather.cli.WeatherCli`。在 IDEA 新建这份运行配置，选择 JDK 21，工作目录仍为本模块。查询天气时，在 **VM options** 中同样配置 `-Damap.api.key=你的Key`；Program arguments 只放下面的任务参数：

| Program arguments | 行为 |
|---|---|
| 留空或 `--help` | 打印用法，不启动 HTTP 服务或调用外部接口 |
| `--weather-check` | 查询高德行政区和预报，需要高德 Key，不调用模型、不需要模型密钥 |
| `--ask=天气问题 --prompt=rules --context=full` | 执行一次真实模型任务，需要在这份 Run 配置中设置 `DEEPSEEK_API_KEY` |

`--weather-check` 先搜索北京，仅在本次结果中有唯一名称为“北京”或“北京市”的候选时，才使用该候选的实际 ID 检查预报接口。它不硬编码地点 ID；普通 Agent 入口仍由模型结合用户要求选择候选。检查报告中的 `kind` 为 `LIVE_WEATHER_TOOL_CHECK`；它没有模型调用，不能用于验证提示词效果。

CLI 的 `--prompt`、`--context` 与 HTTP 的 `promptMode`、`contextMode` 对应，支持相同取值和默认值。任务结束后打印状态与报告路径；任务停止时先保存报告，再以非零状态结束。需要恢复 HTTP 服务时，选择 `WeatherApplication` 的无参数运行配置；清空 `WeatherCli` 的实参只会打印帮助。

如需在终端运行，先在本模块目录打包。`java` 应指向 JDK 21，`mvn` 应在 PATH 中：

```powershell
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw '打包失败。' }
```

打包后的默认主类是 HTTP 启动类。运行辅助命令时，通过 Spring Boot 的 `PropertiesLauncher` 显式指定 `WeatherCli`：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-Dloader.main=example.weather.cli.WeatherCli' -cp target/weather-agent-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --help
```

检查真实天气接口时，先将 `你的Key` 替换为自己的高德 Web 服务 Key。JVM 参数位于 `-cp` 前：

```powershell
java '-Damap.api.key=你的Key' '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-Dloader.main=example.weather.cli.WeatherCli' -cp target/weather-agent-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --weather-check
if ($LASTEXITCODE -ne 0) { throw '天气接口检查失败，请查看生成的报告。' }
```

单次模型任务需先在这个终端的环境中配置好 `DEEPSEEK_API_KEY`，并同样通过 JVM 参数提供高德 Key，再执行：

```powershell
java '-Damap.api.key=你的Key' '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-Dloader.main=example.weather.cli.WeatherCli' -cp target/weather-agent-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher '--ask=北京市明天的天气怎样？请说明降水概率，并给出带伞建议、目标日期和来源。' --prompt=rules --context=full
if ($LASTEXITCODE -ne 0) { Write-Warning '任务未完成；有本次报告路径就先读报告，没有则检查启动错误。' }
```

这些命令执行后退出，不启动 HTTP 服务。直接使用 `java -jar target/weather-agent-0.0.1-SNAPSHOT.jar` 则启动 HTTP 服务，辅助参数不会切换到 `WeatherCli`。

## 已验证的范围

高德迁移的线上边界：用户提供的真实预报响应返回 `status=1`、`infocode=10000`，包含北京市的四天日夜预报，没有降水概率。本机未配置真实高德 Key，因此没有通过完整 Agent 验证线上高德查询；无 Key 的连通性请求只得到鉴权拒绝，不能作为查询成功记录。

2026-10-26 高德迁移后，Maven 离线 `package` 通过（包含主代码编译，跳过 Maven 测试执行）。独立本机检查通过 43 项核心断言、153 项 HTTP 断言、48 项高德字段适配断言、423 项客户端与密钥隔离断言、30 项日志断言；另运行 `EvidenceCheckLab`，12 个合成案例通过。检查使用本机模型或内存天气替身，不调用线上模型或高德天气。以下记录保留切换前的验证范围。

2026-10-26 在 JDK 21 下验证切换高德前的日志改动：Maven 离线 `compile`、`package` 通过，均跳过 Maven 测试执行；独立检查通过 41 项核心断言、153 项 HTTP 断言，以及 30 项日志断言。日志检查覆盖完整工具循环的请求编号传递、并发隔离、正常和异常退出时的 MDC 清理、上下文压缩记录，以及带换行的敏感问题、答案、工具参数和异常消息不进入 `DEBUG` 日志。日志检查程序放在 `.local-checks/WeatherLoggingChecks.java`，结果在 `target/checks/logging/summary.json`；这些检查均未访问线上模型或真实天气服务。

2026-10-25 在 JDK 21 下完成 HTTP 入口验证：

- Maven 离线编译与打包通过，分别执行 `compile`、`package` 并跳过测试执行。
- 真实 Spring MVC 入口配合本机模型替身通过 153 项断言，覆盖 6 次任务、20 次非法请求拒绝及 5 类传输对象的 Java 序列化往返。检查包括无参数启动不调用模型、两种任务状态、默认模式与非法字段、HTTP 400/405/415、独立任务报告，以及响应不暴露报告路径或密钥。
- 核心工具与 Agent 检查通过 41 项断言，覆盖地点隔离、服务错误、未知值、整批工具预检、调用预算、消息配对、压缩信息保留，以及真实 Spring AI 适配器的本机 HTTP 工具循环。
- 打包产物通过 `PropertiesLauncher` 运行 `WeatherCli --help`，正常打印用法并以退出码 0 结束。

检查使用本机模型和天气替身，没有访问线上模型或真实天气服务；这些结果验证接口、消息与执行边界，不代表线上模型回答质量。检查程序是按项目惯例放在 `.local-checks/` 的独立 Java 程序，不是 Maven 自动发现的 JUnit 测试。HTTP 检查结果在 `target/checks/http/summary.json`，对应模型请求在 `target/checks/http/model-requests.json`；核心检查结果在 `target/checks/summary.json`，本机协议请求在 `target/checks/wire-requests.json`。

另保留一项历史记录：2026-10-19 的旧版 `--weather-check` 曾成功访问真实 Open-Meteo 城市和天气接口；它不代表高德接入成功。本轮排障复现了旧服务连接超时，随后迁移到高德。真实模型回答质量、不同网络环境的稳定性、模型 Token 节省和服务端缓存命中尚未验证。
