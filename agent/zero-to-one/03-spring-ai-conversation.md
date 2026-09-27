# Javaer转Agent：让 Spring AI 管理工具循环与多轮对话

上一篇[《用 Java 做第一个 Agent》](02-first-java-agent.md)中，我们写出了资料助手的循环：请求模型，检查工具请求，执行 Java 方法，再把结果交回模型。它能查两份文档，也能记录查找过程，但每次 HTTP 请求都重新开始。

这时连续问两句，就会遇到新的问题：

> 第一轮：订单查询的分页参数怎么传？  
> 第二轮：那它的默认值呢？

第二轮的“它”指什么？上一轮读过的文档还在吗？把手写循环交给框架后，调用次数和停止条件又由谁控制？

这篇继续使用同一个资料助手，按[学习计划](README.md)完成三次变化：**先让框架管理工具循环，再保留多轮对话，最后检查会话隔离与恢复边界。**

配套项目是 [conversation-agent](conversation-agent/README.md)。上一篇的 `first-agent` 保留用于对照；新项目默认监听本机 8081 端口。本文代码来自配套实现，验收部分区分离线模拟检查与真实模型实验。

## 0、先看这次实验改变了什么

资料仍是上一篇的两份教学文档：

| 文档 ID | 内容 |
|---|---|
| `order-api-v2` | 订单查询的分页参数遵循 `pagination-v2` |
| `pagination-v2` | `pageNo` 从 1 开始，`pageSize` 最大为 100，默认值未规定 |

这些数值是固定教学资料，不是分页接口的通用规范。搜索仍然只返回标题和 ID，读取工具才返回正文。

本篇提供两种新入口，便于保持模型和工具不变，只比较历史是否保留：

| 对照方式 | 工具循环由谁控制 | 下一次用户提问是否带历史 |
|---|---|---|
| 上一篇 `first-agent` | 应用中的手写循环 | 不带 |
| 新项目 `/api/chat/stateless` | Spring AI | 不带 |
| 新项目 `/api/chat/sessions/demo-a` | Spring AI | 带 `demo-a` 的会话历史 |

第一次对照，观察手写版与框架版是否都能取得正文依据；第二次对照，向无历史入口和同一会话各连续提问两次；第三次对照，换一个会话 ID 再追问。

先写下预测，再运行。**第二次碰巧答对，不能单独证明程序保存了历史。** 当前文档很少，模型可能根据问题和工具说明重新查到答案；要结合历史消息数量、执行轨迹和消息传递检查来判断。

## 1、准备独立示例

### 1.1 在 IDEA 中启动

在 IDEA 中打开 [conversation-agent/pom.xml](conversation-agent/pom.xml)，选择 JDK 21，等待 Maven 导入完成。这个项目沿用上一篇的依赖组合：Spring Boot 4.1.1、Spring AI 2.0.1，通过 OpenAI 兼容协议适配器调用 DeepSeek。

运行主类：

```text
example.conversation.ConversationApplication
```

在运行配置的 **Environment variables** 中设置 `DEEPSEEK_API_KEY`，**Program arguments 留空**。新项目只提供 HTTP 实验入口，不使用上一篇的 `--guided` 参数。密钥的获取与保管方式见上一篇第 1.2 节，不要将其保存到共享运行配置或提交进仓库。

启动后默认监听：

```text
http://127.0.0.1:8081
```

`AGENT_PORT` 可以修改端口。程序启动本身不提问；发送实际问题后才调用模型并产生用量。无需真实密钥的离线检查见第 6 节。

配置继续使用 `DEEPSEEK_BASE_URL`、`/chat/completions`、`deepseek-flash`，并显式关闭思考模式。具体值见 [application.yml](conversation-agent/src/main/resources/application.yml)。模型参数与协议沿用前例，本篇集中观察循环和消息管理。

### 1.2 先认清几个类

| 文件 | 负责什么 |
|---|---|
| [ChatController](conversation-agent/src/main/java/example/conversation/web/ChatController.java) | 接收问题、校验输入，选择有历史或无历史入口 |
| [ConversationAgent](conversation-agent/src/main/java/example/conversation/service/ConversationAgent.java) | 创建客户端、选择会话、管理每轮调用与失败后的历史恢复 |
| [TurnGuard](conversation-agent/src/main/java/example/conversation/service/TurnGuard.java) | 检查模型次数、工具预算和耗时，记录每轮调用 |
| [DocTools](conversation-agent/src/main/java/example/conversation/tool/DocTools.java) | 搜索和读取两份固定文档 |

从调用入口看，本篇的变化是：

```text
用户提交问题
  → ConversationAgent 选择会话与客户端
  → ChatClient 的 Advisor 链
      → 读取会话历史（有历史入口）
      → 框架执行工具循环
          → TurnGuard 检查本轮预算
          → 调用模型
          → TurnGuard 检查返回的工具请求
          → 框架执行工具、回填结果，再进入下一轮
      → 保存本轮用户问题和最终回复
  → 返回候选答案与轨迹
```

这里的 Advisor 可以理解为调用过程中的一个处理环节。它的位置决定自己看到的是一次用户提问，还是工具循环中的每次模型请求。下面通过实际代码解释。

## 2、先把手写循环交给框架

### 2.1 循环仍然存在，只是执行位置变了

上一篇由应用反复调用 `model.call(prompt)`，收到工具请求后调用 `ToolCallingManager`，再用新的历史构造下一轮请求。

本篇在 `ConversationAgent.run()` 中发起一次调用：

```java
var request = client.prompt().user(question).options(options)
        .tools(new DocTools(trace::add)).advisors(guard);
if (id != null) request.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id));
var response = request.call().chatResponse();
```

这是方法节选。`client` 是已经配置好的客户端，`trace`、`guard` 和工具对象都属于本次用户提问，`id` 仅在有历史入口提供。`options` 是 `OpenAiChatOptions.builder()` 返回的构建器；本项目的 Spring AI 2.0.1 `ChatClient.options()` 接受构建器，不能直接照搬上一篇传给 `Prompt` 的已构建选项对象。

对 Java 调用方而言只执行了一次 `call()`，内部可能进行了多次模型请求和工具执行。Spring AI 2.0 的 `ToolCallingAdvisor` 负责这个过程，底层工具仍由 Java 执行。[Spring AI 工具调用说明](https://docs.spring.io/spring-ai/reference/api/tools.html)

不要在这段代码外再套一层“检测工具请求、手动执行工具”的旧循环。迁移时应先明确当前路径由谁执行工具，避免重复调用。

### 2.2 为什么这里显式配置工具循环

配套代码将总工具调用上限设为 8，并关闭按名称查找未随本次请求注册的工具：

```java
var manager = ToolCallingManager.builder()
        .maxTotalToolCalls(8).resolutionFallbackEnabled(false).build();
var tools = ToolCallingAdvisor.builder().toolCallingManager(manager).build();
```

随后将这个 `tools` Advisor 交给客户端。本篇显式配置它，是为了清楚地指定工具管理器；这些对象的创建没有重新实现循环。

两份公开文档和两个工具依旧不构成多用户权限系统。这里限制的是程序开放的工具范围，真实项目还需要根据可信身份检查能读取哪些资料。

### 2.3 保留调用预算，而不是只删掉循环

原例限制每次用户提问最多 6 次模型调用、8 次工具调用，并在模型请求前后检查 5 分钟任务预算。本篇保留这些规则，放入每轮新建的 `TurnGuard`。

它位于工具循环内部：

```java
@Override
public int getOrder() {
    return ToolCallingAdvisor.DEFAULT_ORDER + 100;
}
```

因此每一次模型请求都会经过它。返回结果带工具请求时，它在框架执行工具前检查整批请求：

```java
if (modelCalls == MAX_MODEL_CALLS || calls.size() > MAX_TOOL_CALLS - toolCalls)
    throw new Stopped("剩余调用预算不足，尚未完成。");
if (calls.stream().anyMatch(call -> !ALLOWED.contains(call.name())))
    throw new Stopped("模型请求了未开放的工具。");
```

第 6 次模型调用如果仍要求工具，就停止：执行之后已经没有下一次模型调用读取结果的机会。单次返回 9 个工具请求，也会在执行整批工具前被拒绝。

`TurnGuard` 只检查并返回结果；执行工具、关联调用 ID 和组织下一轮消息由框架负责。它使用每轮独立的计数器，不把不同会话的调用次数累加在一起。

这里的耗时预算不是强制中断：检查发生在模型请求前后，无法立刻终止正在阻塞的网络请求。单次 HTTP 等待由客户端的 120 秒超时配置约束。示例只提供同步 `call()`，没有实现流式接口。

## 3、让第二次提问接上第一次

### 3.1 先创建真正跨请求存在的记忆对象

如果每次处理问题时都创建一份新历史，方法返回之后就无法延续会话。本篇将 `ChatMemory` 放在 Spring 服务实例中，并由会话 ID 区分消息：

```java
private final ChatMemory memory =
        MessageWindowChatMemory.builder().maxMessages(20).build();
```

客户端创建时，加入管理历史的 Advisor：

```java
this.stateful = ChatClient.builder(model).defaultSystem(SYSTEM)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(), tools)
        .build();
this.stateless = ChatClient.builder(model).defaultSystem(SYSTEM)
        .defaultAdvisors(tools).build();
```

两个客户端使用相同模型、规则和工具循环，区别是是否加入会话记忆。20 是消息窗口的配置上限，不是承诺保存 20 轮问答；超过窗口后会发生淘汰，不能把它当完整聊天档案。[Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)

### 3.2 会话 ID 怎样进入请求

有历史入口把路径中的 `demo-a` 传给服务，再通过下面的参数交给记忆 Advisor：

```java
request.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id));
```

这一步决定本次从哪份会话中取历史、向哪份会话写入消息。无历史入口使用另一个客户端，不读取这些会话。

可以先在 PowerShell 中定义一个辅助函数：

```powershell
function Ask-Doc {
    param([string]$Path, [string]$Question)
    $body = @{ question = $Question } | ConvertTo-Json -Compress
    $params = @{
        Method = 'Post'
        Uri = 'http://127.0.0.1:8081/api/chat/' + $Path
        ContentType = 'application/json; charset=utf-8'
        Body = [Text.Encoding]::UTF8.GetBytes($body)
    }
    Invoke-RestMethod @params
}
```

然后依次执行：

```powershell
Ask-Doc 'stateless' '订单查询的分页参数怎么传？请给出文档依据。'
Ask-Doc 'stateless' '那它的默认值呢？'

Ask-Doc 'sessions/demo-a' '订单查询的分页参数怎么传？请给出文档依据。'
Ask-Doc 'sessions/demo-a' '那它的默认值呢？'

Ask-Doc 'sessions/demo-b' '那它的默认值呢？'
```

先看 `trace` 中“本轮开始前历史消息数”，再看工具请求、正文返回与候选答案。有历史的第二轮应能读取此前的用户与助手消息；新会话和无历史入口不应带上 `demo-a` 的历史。

具体措辞和工具顺序取决于真实模型运行。即使无历史入口也答对了“默认值未规定”，仍要检查它是重新查到了资料，还是仅凭上下文猜测。

### 3.3 上一轮的工具正文去了哪里

本篇让记忆 Advisor 位于工具循环外。离线检查实际观察到：

- 本次任务内部，后续模型请求能收到刚执行的工具结果。
- 本轮结束后，会话保留用户问题与最终助手回复。
- 下次提问开始时，记忆中没有上轮中间的工具请求和工具响应。

所以，“模型本轮读过资料”和“下次仍保留完整资料”是两个问题。历史回答有助于理解“它”指什么，却不能直接作为项目事实的原始依据。本例要求回答具体参数前重新读取正文。

若后续确实需要保留完整工具轨迹，要另外核对 Advisor 的位置、消息存储支持和窗口策略。当前练习先把这两层历史分开看清楚。

### 3.4 隔离会话还要处理重叠请求

将历史按 ID 存储，不能自动保证同一会话的两次请求顺序正确。例如两个请求同时读到同一份旧历史，然后各自追加回复，就可能打乱对话顺序。

本例为每个会话保留一把锁。同一会话还在运行时，后来的提问返回 `STOPPED`，提示等待上一轮结束；不同会话可以独立执行。清空会话也经过同一把锁。

为避免演示服务无限创建内存对象，最多创建 32 个会话 ID。清空只删除消息，不释放会话名额；达到上限后重启示例再实验。这个简单限制没有实现会话过期淘汰，也不是面向生产的会话服务。

更重要的是，**会话 ID 只是分组键，不是用户身份。** 这个本机教学接口允许调用方指定 ID；真实服务必须验证调用方是否拥有对应会话，不能直接开放给多用户使用。

## 4、信息不足时先问，再继续

这次沿用原有 v2 文档，不新增一个虚构的 v1 参数规范。把提问改成：

```powershell
Ask-Doc 'sessions/version-demo' '我的项目应该用哪套分页规则？我还没告诉你项目版本。'
Ask-Doc 'sessions/version-demo' '我使用 v2，请继续查文档并给出来源。'
```

系统规则要求：用户想判断自己项目适用的规则，却未提供版本时，先追问；补充 v2 后再读取现有文档。若补充的是其他版本，则说明没有对应资料。

这是一项真实模型待核验的行为要求，不是保证模型每次都会遵守。离线脚本可以验证追问消息是否保留、补充消息是否进入下一轮，真实模型是否适时追问仍需实际运行检查。

这里也没有新增持久化任务状态机。第一轮返回追问，属于本轮的一次正常回复；第二轮是携带历史的新调用。接口中的 `COMPLETED` 表示本轮产生了候选回复，回复可能是答案，也可能是追问，不代表用户的整体任务已经解决。

验收时检查三件事：追问是否说明缺少什么；补充信息是否关联到原问题；最后的事实结论是否得到文档支持。

## 5、失败、清空和重启分别意味着什么

### 5.1 失败后不留下半轮会话

记忆组件可能在调用模型前就写入用户消息。如果模型随后超时或预算耗尽，只返回错误而不处理历史，下一轮就可能看到一条没有正常回复的残留问题。

本例在会话锁内保存本轮开始前的历史快照。结果为 `STOPPED` 时，恢复这份快照：

```java
var before = List.copyOf(memory.get(conversationId));
Result result = run(stateful, conversationId, question, before.size());
if (result.status().equals("STOPPED")) {
    memory.clear(conversationId);
    if (!before.isEmpty()) memory.add(conversationId, before);
}
```

恢复只影响对话历史。已经发生的模型调用用量不会撤销；如果以后增加业务写操作，也不能用这种消息回滚代替业务补偿。本篇工具只读取公开固定文档。

### 5.2 清空后重新实验

清空 `demo-a`：

```powershell
Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8081/api/chat/sessions/demo-a'
```

再提问时，轨迹中的历史消息数应从空会话开始。会话正在执行时，清空会返回停止提示，避免一边生成回复一边删除历史。

### 5.3 重启没有自动恢复能力

本例使用进程内记忆。停止 IDEA 中的服务，再启动后，用同一个 ID 提问，也不会自动找回上一进程的历史。

读到这里，应能区分：

| 状态 | 本例怎样处理 |
|---|---|
| 本轮模型与工具之间的消息 | 框架工具循环维护，用于继续执行 |
| 多次用户提问之间的历史 | 内存会话保存，受消息窗口限制 |
| 重启后恢复未完成任务 | 未实现，没有持久化检查点 |

后续即使将聊天消息存入数据库，也仍需回答：哪些工具已执行、哪些结果已确认、从哪里接续、怎样避免重复操作。保存聊天记录只是恢复设计中的一部分。

## 6、怎样判断这篇练习完成了

### 6.1 先运行无需密钥的检查

在 `conversation-agent` 目录中执行：

```text
mvn test
mvn package
```

也可以在 IDEA 中运行 [ConversationAgentTest](conversation-agent/src/test/java/example/conversation/service/ConversationAgentTest.java) 与 [HttpIntegrationTest](conversation-agent/src/test/java/example/conversation/service/HttpIntegrationTest.java)。

第一组使用脚本模型返回预设响应，但真正运行 Spring AI 客户端、工具循环、Java 工具和记忆组件。第二组启动本机模拟接口，经过真实 HTTP Controller 与模型协议适配器，检查请求地址、消息内容和工具结果回填。模拟接口的响应不代表 DeepSeek 的真实回答。

本次已在 JDK 21 下通过编译、打包和 **13 项自动检查**，包括：

| 验证范围 | 检查内容 |
|---|---|
| 框架循环 | 工具实际执行，正文回填后再次调用模型 |
| 多轮与隔离 | 同会话携带历史，新会话与无历史入口不携带它 |
| 追问继续 | 脚本追问与用户补充信息进入后续上下文 |
| 记忆边界 | 外层记忆不保存中间工具消息，新建服务实例没有旧历史 |
| 停止与失败 | 未开放工具、工具超额、模型次数耗尽、空回答、超时预算、失败历史恢复 |
| 并发与清空 | 同会话重叠提问和运行中清空被拒绝，其他会话可以独立运行 |
| HTTP 与协议 | 空问题和非法 ID 返回 400；模型名、关闭思考的参数和工具消息进入模拟请求 |

其中耗时测试使用可控时钟跨过预算边界，没有真实等待五分钟；恢复边界测试新建服务实例，没有声称完成了真实进程的持久化恢复。

### 6.2 再记录真实模型的表现

本篇没有使用真实 DeepSeek 密钥执行线上验证。配置好密钥后，按第 3、4 节运行，并记录：

| 实验 | 通过依据 |
|---|---|
| 框架版查询分页规则 | 轨迹显示读取正文，答案与文档一致 |
| 连续追问默认值 | 指代清楚，依据仍是文档，不能编造默认值 |
| 换会话再追问 | 不继承其他会话的信息；信息不足时说明或追问 |
| 补充项目版本 | 能关联原问题，v2 查现有资料，其他版本说明缺少依据 |
| 清空或重启 | 不再依赖已经清除或丢失的历史 |

把输入、实际回答、工具轨迹和停止原因一起保存，不只记录“成功”。如果回答正确但没有本轮读取依据，或者新会话沿用了另一会话的内容，就回到消息和执行记录定位原因。

下一步按问题选择：文档增多且搜索不准时补 RAG；历史变长时研究上下文组织；需要重启恢复时再设计任务状态与持久化。当前先能解释框架循环和两层历史，再继续扩展。

## 7、参考资料与阅读范围

- [Javaer 转 Agent：学习资料篇](https://tyron.me/posts/agent-resources)：使用其中 Spring AI、工程验证和上下文主题的资料导航；[本地资料篇](00-agent-resources.md)保留完整入口。
- [深入理解 AI Agent：第一章](https://bojieli.github.io/ai-agent-book/book/chapter1/)：借鉴控制变量、观察上下文与执行反馈的学习方法。
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)：核对框架工具循环、工具注册及记忆与循环的关系。
- [Spring AI ToolCallingAdvisor](https://docs.spring.io/spring-ai/reference/api/tools/tool-calling-advisor.html)：核对循环扩展位置与 Advisor 顺序。
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)：核对会话 ID、内存窗口和存储边界。
- [Spring AI 官方示例](https://github.com/spring-projects/spring-ai-examples)：遇到接入问题时，选择与依赖版本匹配的最小示例。
- [Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)：将执行轨迹与任务是否完成分开评估。

文中 Spring AI 行为已结合项目依赖 2.0.1 与离线检查核对。在线文档会更新，后续升级时应重新执行同一组检查。
