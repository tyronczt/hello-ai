# first-agent：通过 HTTP 提问的 Java 文档助手

这是[《用 Java 做第一个 Agent》](../02-first-java-agent.md)的独立 Maven 示例。调用方提交问题，Controller 完成 Web 对象转换，`DocAgent` 处理一次请求，返回候选答案与实际工具轨迹。启动服务不会自动向模型提问。

`example.agent.AgentApplication` 只负责启动；`service.DocAgent` 执行 Agent 循环，`tool.DocTools` 提供只读工具，`demo.DemoRunner` 承载命令行教学流程。引导内容由 SLF4J 日志输出。

环境：JDK 21、Maven 3.9.x、Spring Boot 4.1.1、Spring AI 2.0.1。模型使用线上 DeepSeek `deepseek-flash`，密钥只从 `DEEPSEEK_API_KEY` 环境变量读取。

## 启动服务

在本目录打开 PowerShell，安全地给当前终端设置密钥，然后启动：

```powershell
$credential = Get-Credential -UserName "deepseek" -Message "在密码栏输入 DeepSeek API Key"
$env:DEEPSEEK_API_KEY = $credential.GetNetworkCredential().Password
Remove-Variable credential
mvn -q -DskipTests package
java -jar target/first-agent-0.0.1-SNAPSHOT.jar
```

默认监听 `127.0.0.1:8080`。`AGENT_PORT` 可改端口；只有放在可信鉴权入口之后，才应考虑设置 `AGENT_BIND_ADDRESS` 接受其他设备的连接。

在 IntelliJ IDEA 中打开 `pom.xml` 并选择 JDK 21。新建 `example.agent.AgentApplication` 运行配置，把 `DEEPSEEK_API_KEY` 填在 **Environment variables**，**Program arguments 留空**，运行后服务会持续监听。不要勾选 **Store as project file / Share through VCS**；运行配置可能以明文保存密钥。IDEA Terminal 中设置的环境变量不会自动传给已启动 IDEA 的 Run 配置。

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

`COMPLETED` 仅表示得到候选答案，仍需核对实际读取的文档。`STOPPED` 的 `answer` 是停止原因，不能作为问题答案。空问题或超过 1000 字符由 Web 入参校验返回 HTTP 400；模型或工具中途停止会返回 HTTP 200 和 `status: STOPPED`，调用方应检查 `status`。

## 多用户边界

每次 POST 都重新建立消息历史、工具实例、调用预算和 `trace`；并发请求不共享这些可变状态，也不会把上一个用户的问题作为本次上下文。当前示例**没有跨请求会话记忆**。两份固定教学文档对所有调用方公开，因此轨迹中会展示正文；改为真实文档时，必须从可信身份获取用户权限，在搜索和读取两处校验，并收紧响应轨迹。

这个示例没有登录、租户隔离、限流和用量配额。默认只监听本机；接入多用户前需由现有网关或应用认证层负责身份、授权、限流，再放开监听地址。

## 可选：四阶段引导练习

需要研究上下文、知识库和工具的差别时运行：

```powershell
java -jar target/first-agent-0.0.1-SNAPSHOT.jar --guided
```

程序会要求你**输入问题**，再逐阶段预测、选择运行或跳过。单阶段复查可用 `java -jar target/first-agent-0.0.1-SNAPSHOT.jar --stage=3 "你的问题"`。这两个模式是本地教学入口，不启动 HTTP 服务。

## 验证

```powershell
mvn -q -DskipTests package
python .local-checks/check_agent.py
python .local-checks/check_http.py
```

本地模拟模型检查可以验证请求字段、工具调用回填、HTTP 参数校验和并发隔离；它不证明真实 DeepSeek 的工具选择与答案质量。项目中被 Git 忽略的 `.local-checks/check_agent.py` 检查七类工具循环场景，`.local-checks/check_http.py` 检查 POST、空问题和三个并发请求；两个脚本都使用虚拟密钥与本机模拟服务。
