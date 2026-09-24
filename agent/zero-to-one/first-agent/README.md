# first-agent：通过 HTTP 提问的 Java 文档助手

这是[《用 Java 做第一个 Agent》](../02-first-java-agent.md)的示例项目。你提交一个订单文档问题，程序查找固定的两份教学资料，再返回答案和查找过程。启动服务时不会自动向模型提问。

先认四个类：`AgentApplication` 决定启动 HTTP 服务还是练习；`DemoRunner` 带你做四阶段练习；`DocAgent` 决定什么时候问模型、什么时候查资料；`DocTools` 提供两份固定教学文档。练习中的提示和结果会出现在 IDEA 的 Run 控制台。

环境：JDK 21、Maven 3.9.x、Spring Boot 4.1.1、Spring AI 2.0.1。模型使用线上 DeepSeek `deepseek-flash`，密钥只从 `DEEPSEEK_API_KEY` 环境变量读取。

## 启动服务

在 IntelliJ IDEA 中打开 `pom.xml`，选择 JDK 21，创建主类为 `example.agent.AgentApplication` 的运行配置：

1. 在 **Environment variables** 中设置 `DEEPSEEK_API_KEY`。
2. **Program arguments 留空**，点击运行；服务启动后持续监听 `127.0.0.1:8080`，等待用户提交问题。

不要勾选 **Store as project file / Share through VCS**；运行配置可能以明文保存密钥。IDEA Terminal 中设置的环境变量不会自动传给工具栏的 Run 配置。`AGENT_PORT` 可改端口；只有放在可信鉴权入口之后，才应考虑设置 `AGENT_BIND_ADDRESS` 接受其他设备的连接。

## 用户提交问题

请求只包含本次问题，不传用户 ID，也不开放教学用的 `stage` 参数：

```http
POST /api/agent/ask
Content-Type: application/json

{"question":"订单查询的分页参数怎么传？请给出文档依据。"}
```

PowerShell 调用示例：

```powershell
$body = @{ question = '订单查询的分页参数怎么传？请给出文档依据。' } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/agent/ask' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

响应结构如下；内容是示意，真实模型的工具顺序和措辞可能不同：

```json
{
  "status": "COMPLETED",
  "answer": "pageNo 从 1 开始，pageSize 最大为 100；资料未规定默认值。来源：pagination-v2。",
  "trace": [
    "[模型调用 1/6]",
    "请求工具：readDoc，调用 ID：...",
    "readDoc 返回：来源：pagination-v2 / 公共分页约定 v2\n正文：..."
  ]
}
```

看返回值时先看 `status`：`COMPLETED` 表示有候选答案，还要核对 `trace` 中读过的文档；`STOPPED` 表示任务中途停止，`answer` 里写的是停止原因。空问题或超过 1000 字符会返回 HTTP 400。模型或工具中途停止时，HTTP 可能仍返回 200，所以不能只看 HTTP 状态码。

## 多次提问会互相影响吗

每次 POST 都从一张“新纸”开始：重新建立消息记录、工具调用次数和 `trace`。前一个用户的问题不会带进后一个请求；同一个用户连续问两次，也不会自动接着上一次聊。本例没有保存聊天记忆。

两份教学文档对所有调用方公开，所以这里会把正文放进 `trace`。真实业务文档不能这样直接返回。这个示例也没有登录、租户隔离、限流和用量配额；给多设备、多用户使用前，要先接入身份认证和权限检查，再开放本机以外的访问。

## 可选：四阶段引导练习

把 IDEA 运行配置的 **Program arguments（程序实参）** 改为 `--guided`，重新运行。`DemoRunner` 会请你输入问题，再依次展示四个阶段准备给模型看的内容。每轮可以先猜答案，再选择：

- 在“预测”提示处输入一次 `s`：直接跳过这一轮。没有 API Key 也能这样看预览，但看不到模型的真实回答。
- 想看真实回答：先写下预测，再在下一步“回车运行”处按回车。此时会调用 `DocAgent` 和线上 DeepSeek，需要设置 `DEEPSEEK_API_KEY`，并会产生用量。
- 输入 `q`：退出。

`DemoRunner` 只负责引导和展示，不会自己编一个模拟答案。若只复查某一阶段，把程序实参改为 `--stage=3 "你的问题"`；它会直接调用模型。练习模式不提供 HTTP 接口。要重新用 POST 提问，请清空程序实参后运行。

## 在 IDEA 中看执行日志

每次实际运行都有一个 `taskId`，用它可以从交错的多用户日志中找到同一次任务。INFO 显示模型轮次、工具名称与执行耗时，WARN 显示停止原因，ERROR 显示异常类型和堆栈位置。`DocAgent` 不主动记录问题、候选答案、密钥或文档正文；接口返回的教学 `trace` 仍包含两份固定公开文档的正文。`--guided` 的控制台预览会显示你输入的问题，请勿输入真实敏感信息。本例只在 IDEA 控制台输出日志；正式部署时再按环境要求配置日志归档和保留期限。

## 编译检查（可选）

```powershell
mvn -q -DskipTests compile
```

这只检查代码能否编译。要看模型是否真的读取文档，请在 IDEA 中启动服务并发送上面的 POST，然后检查响应里的 `trace`。
