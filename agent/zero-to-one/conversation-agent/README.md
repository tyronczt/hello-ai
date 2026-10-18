# conversation-agent：框架工具循环与多轮对话（学习路径已废弃）

本示例对应的框架工具循环与内存会话学习路径已废弃，不再作为系列主线或补充实验。当前学习入口见[学习路线](../README.md)。以下保留既有示例的运行与验证说明。

## 运行

在 IDEA 中打开本目录的 `pom.xml`，选择 JDK 21，运行 `example.conversation.ConversationApplication`。Program arguments 留空；Environment variables 设置 `DEEPSEEK_API_KEY`，不要将带密钥的运行配置共享或提交。

沿用 Spring Boot 4.1.1、Spring AI 2.0.1 和 DeepSeek `deepseek-flash`。服务默认只监听 `127.0.0.1:8081`，可用 `AGENT_PORT` 修改端口。实际模型请求会产生用量。

## HTTP 实验

每个 POST 的请求体都为 `{"question":"你的问题"}`，使用 UTF-8 JSON。

| 方法与路径 | 用途 |
|---|---|
| `POST /api/chat/stateless` | 框架工具循环，不保留跨请求历史 |
| `POST /api/chat/sessions/demo-a` | 框架工具循环，保留 demo-a 会话 |
| `POST /api/chat/sessions/demo-b` | 独立的 demo-b 会话 |
| `DELETE /api/chat/sessions/demo-a` | 清空 demo-a 历史 |

PowerShell 请求示例：

```powershell
$body = @{ question = '订单查询的分页参数怎么传？请给出文档依据。' } | ConvertTo-Json -Compress
$params = @{
    Method = 'Post'
    Uri = 'http://127.0.0.1:8081/api/chat/sessions/demo-a'
    ContentType = 'application/json; charset=utf-8'
    Body = [Text.Encoding]::UTF8.GetBytes($body)
}
Invoke-RestMethod @params
```

保持会话 ID 再问“那它的默认值呢？”，然后换成 demo-b 重复追问。再将路径改成 `stateless` 比较无历史请求，或重启服务后观察同一 ID 的历史。结合 `trace` 中的历史消息数、工具返回和答案依据判断结果。资料依旧只有 v2；版本追问练习不新增 v1 事实。

响应包含 `status`、`answer`、`trace`。`COMPLETED` 表示本轮生成候选回复，可能是答案或追问；它不代表事实已经核验。`STOPPED` 表示失败、预算停止或会话繁忙；`CLEARED` 表示清空完成。工具轨迹包含固定公开教学文档正文，不适合直接套用到真实私有资料。

## 能力与边界

- ToolCallingAdvisor 管理循环；每轮 TurnGuard 保留 6 次模型调用、8 次工具调用和 5 分钟轮次间预算，单次网络请求配置 120 秒超时。
- MessageChatMemoryAdvisor 位于工具循环外，保留用户与最终助手消息；中间工具消息只在本轮循环中使用。
- 每个会话配置 20 条消息窗口，同一会话重叠执行或运行中清空会被拒绝。
- 内存最多创建 32 个会话 ID；清空不释放名额，重启后清空全部状态。没有持久化、会话过期淘汰或任务恢复。
- 会话 ID 仅作分组，没有登录鉴权，不应直接作为真实多用户服务。
- 仅提供同步调用，没有流式接口。失败恢复的是历史快照，不会撤销模型用量或业务副作用。

## 无密钥验证与打包

在本目录执行：

```text
mvn test
mvn package
```

也可以直接在 IDEA 运行两个测试类。测试不需要真实密钥、不访问真实 DeepSeek；首次解析 Maven 依赖可能需要联网。

- `ConversationAgentTest`：12 项脚本模型检查，实际执行框架循环、工具与记忆，验证隔离、追问、预算、并发和失败恢复。
- `HttpIntegrationTest`：1 项集成检查，启动本机随机端口模拟模型服务，经过真实 HTTP 入口和协议适配器，验证参数、工具回填、历史、清空及 400 响应。

该示例的既有离线验证记录为 `mvn package` 通过 13 项检查并生成可运行 JAR；本次结果以重新执行上述命令为准。真实 DeepSeek 的回答质量和追问行为尚未验证；离线模型的预设响应不能替代线上验收。构建后也可在已配置密钥的终端用 `java -jar target/conversation-agent-0.0.1-SNAPSHOT.jar` 启动。
