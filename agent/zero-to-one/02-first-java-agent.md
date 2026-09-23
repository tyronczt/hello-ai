# Javaer转Agent：用 Java 做第一个 Agent

上一篇[《什么是 Agent》](01-what-is-agent.md)讲了一个资料助手。这次我们用 Java 做出它：你问“订单查询的分页参数怎么传”，它去找文档、读正文，再根据读到的内容回答。文档只有两份，方便你看清每一步。

[完整代码：GitHub / agent/zero-to-one](https://github.com/tyronczt/hello-ai/tree/main/agent/zero-to-one)。本文只展示理解机制所需的关键代码，完整项目、配置与注释以仓库为准。

先在 IDEA 中用 `--guided` 做一个不调用模型的练习：输入自己的问题，看看四个阶段分别准备了哪些内容。**只看预览不需要 API Key，也不产生模型调用费用。**看懂之后，再决定是否配置 Key 运行模型；HTTP 接口放在第 5 节讲。

## 0、先用 `--guided` 看四次变化

在 IDEA 中打开 `first-agent/pom.xml`，选择 JDK 21，运行 `example.agent.AgentApplication`。在运行配置的 **Program arguments（程序实参）** 中填 `--guided`。程序会在 Run 控制台等你输入问题。

接下来一直用同一个问题，例如“订单查询的分页参数怎么传？”。每一轮，程序先展示**准备给模型看的内容**，你先猜它会怎么答，再选择：

- 在“你预测它会怎样回答”处直接输入 `s`：跳过本轮，不调用模型。没有 Key 也可以这样看完四轮的预览。
- 想看真实回答：先写下预测，再在下一步“回车运行”处按回车。这会调用 DeepSeek，需要在运行配置的 **Environment variables（环境变量）** 中设置 `DEEPSEEK_API_KEY`，并会产生用量。
- 输入 `q`：退出练习。

空问题不会执行。Key 的配置和保管方式见第 2.2 节。

如果这次只想免费预览，每轮看到“你预测它会怎样回答”时直接输入一次 `s`。四轮都不会发起线上请求。想认真对照结果时，先写下预测，再在下一步决定按回车运行还是输入 `s` 跳过。

下面是只看预览、跳过这一轮的操作。`[system]` 是给模型的回答规则，`[user]` 是你的问题：

```text
=== 阶段 1 增加任务规则 ===
本轮模型收到：
[system] ...只依据实际读到的正文回答...
[user] 订单查询的分页参数怎么传？请给出文档依据。
你预测它会怎样回答或行动？输入 s 跳过，输入 q 退出：
> s
=== 阶段 2 放入两份文档 ===
```

这里选 `s`，所以没有实际回答，直接进入下一阶段。若选回车，才会显示“实际执行”和模型回答。第 3 阶段实际运行后，还可以看它请求了哪些工具、Java 返回了什么。屏幕上的预览是便于阅读的摘要，不是发给模型的原始 HTTP 报文。

四轮使用同一个模型。变化的是每轮给它的信息和可用动作：

| 阶段 | 给模型什么 | 你要观察什么 |
|---|---|---|
| 0：只有问题 | 你输入的问题 | 没有项目资料时，答案可能只是猜测 |
| 1：加回答规则 | 问题，加上“按资料回答，未知就说未知” | 有规则，但仍没有资料，模型能否承认不知道 |
| 2：直接给资料 | 问题、规则和两份文档正文 | 模型能否从正文找出参数，并指出没写默认值 |
| 3：给查资料的工具 | 问题、规则，以及“搜索文档”“读取文档”两个动作 | 模型请求查什么；Java 返回正文后，它怎样继续 |

代码里，`UserMessage` 装你的问题，`SystemMessage` 装回答规则。第 2 阶段还会用 `DocTools.demoContext()` 把两份固定文档一起放进去。`DemoRunner` 的预览和真正执行都调用下面这个方法，因此它们准备的初始消息相同：

```java
public static List<Message> initialMessages(String question, int stage) {
    if (stage == 0) {
        return List.of(new UserMessage(question));
    }
    return List.of(new SystemMessage(SYSTEM), new UserMessage(stage == 2
            ? "教学资料：\n" + DocTools.demoContext() + "\n问题：" + question : question));
}
```

把资料随这一次请求发给模型，叫“放进上下文”。这里的两份文档存在 Java 程序里，模型只能看到本次请求带过去的内容。它不会记住上一个用户问过什么；那种跨请求保存历史的功能叫“会话记忆”，本例没有做。

第 3 阶段先不发送文档正文，只告诉模型“可以搜索、可以读取”。模型提出想调用哪个工具，Java 才去执行；执行结果放进下一次模型请求。搜索只给 ID 和标题，读取才给正文。为什么要分两步，第 3、4 节会对照代码讲。

判断练习是否成功，不能只看数字有没有猜对。第 0、1 阶段即使说对 `pageSize=100`，也没有读到项目文档；第 2 阶段应能从正文找到依据；第 3 阶段还应在执行记录中看到它读了 `pagination-v2`。模型每次选择的搜索顺序可能不同。

需要复查某一轮时，把 IDEA 运行配置的 **Program arguments** 改为 `--stage=2 "你的问题"`，重新运行。清空该参数后启动 HTTP 服务，不会自动提问。

## 1、看看程序手里有什么资料

用户输入：

> 订单查询的分页参数怎么传？请给出文档依据。

教学资料只有两份：

| 文档 ID | 标题 | 正文要点 |
|---|---|---|
| `order-api-v2` | 订单查询接口 v2 | 分页规则见 `pagination-v2` |
| `pagination-v2` | 公共分页约定 v2 | `pageNo` 从 1 开始，`pageSize` 最大为 100，未规定默认值 |

这些参数是本篇编的教学资料，不是通用规范。第一份文档只说“去看公共分页约定”，第二份才写了具体数值。第 2 阶段把两份正文直接交给模型；第 3 阶段让模型自己用工具去取。

程序提供两个动作：`searchDocs` 根据关键词返回文档 ID 和标题，`readDoc` 读取指定正文。至于先查什么、读到引用后是否继续查，由模型根据结果选择。

本篇使用固定、公开的教学数据，没有文件写入和业务操作。HTTP 请求可以由多个用户同时发起；真实资料的权限、身份和会话记忆仍需另行实现。

## 2、准备环境和项目

### 2.1 本篇使用的技术组合

第一次跟着做，只需确认 IDEA 使用 JDK 21、项目能导入 Maven，并准备 DeepSeek API Key。下面的 Spring Boot 和 Spring AI 版本已经写在项目的 `pom.xml` 中，不用在 IDEA 里逐项填写。

| 项目 | 本篇选择 |
|---|---|
| JDK | 21 |
| Maven | 3.9.x |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| 模型服务 | DeepSeek 线上 API：`https://api.deepseek.com` |
| 模型 | `deepseek-flash` |
| 范围 | 本篇只验证 DeepSeek 主线；本地模型作为后续迁移练习 |

截至 2026-09-23，Spring 官方把 Spring AI 2.0.1 列为最新稳定版；它面向 Spring Boot 4.0/4.1，本例使用已发布的 Boot 4.1.1。版本号是本篇可复现组合，不是要求你在自己的业务项目里立刻升级。[Spring AI 稳定版本](https://docs.spring.io/spring-ai/reference/spring-projects.html) · [2.0 升级说明](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) · [Spring Boot 4.1.1 运行要求](https://docs.spring.io/spring-boot/system-requirements.html)

DeepSeek 官方文档给出的模型名是 `deepseek-flash`，对话接口为 `https://api.deepseek.com/chat/completions`。本文通过 Spring AI 的 OpenAI 兼容协议适配器访问这个地址，实际服务和密钥均来自 DeepSeek。[DeepSeek 首次调用 API](https://api-docs.deepseek.com/zh-cn/)

本篇使用非思考模式，把注意力放在工具循环上。DeepSeek 默认开启思考模式，开启后还需要遵守 `reasoning_content` 的历史回传要求，因此配置会显式发送 `thinking.type: disabled`，不能仅靠删掉思考参数来关闭。[DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)

### 2.2 准备 DeepSeek API Key

只看 `--guided` 的预览，可以先跳过本节。要按回车运行阶段，或通过 HTTP 请求取得模型答案，再配置 Key。

在 [DeepSeek 开放平台](https://platform.deepseek.com/)创建 API Key，确认账户可调用 API。线上请求会产生用量，先使用本文两份教学资料，密钥通过环境变量提供，不写进代码或仓库。

在 IDEA 的 `example.agent.AgentApplication` 运行配置中，将 Key 填入 **Environment variables** 的 `DEEPSEEK_API_KEY`，具体操作见第 5.4 节。不要打印密钥，也不要把它写进代码、日志或共享运行配置。这里不配置本地模型，Ollama 的安装与切换放在备选小节。

### 2.3 打开现成的 Maven 项目

完整示例已经在仓库的 `first-agent` 目录中。用 IDEA 打开它的 `pom.xml` 即可，不必照着下面的目录图手工创建文件。先认三个类：`AgentApplication` 负责启动，`DemoRunner` 带你做练习，`DocAgent` 负责调用模型和工具。

项目目录如下，其他类会在用到时再介绍：

```text
first-agent/
├── pom.xml
└── src/main/
    ├── java/example/agent/
    │   ├── AgentApplication.java
    │   ├── demo/DemoRunner.java
    │   ├── service/DocAgent.java
    │   ├── tool/DocTools.java
    │   ├── dto/{AskDTO,AgentResultDTO}.java
    │   └── web/{AgentController,query/AskQuery,vo/AskVO}.java
    └── resources/application.yml
```

[GitHub 上的完整 Maven 项目](https://github.com/tyronczt/hello-ai/tree/main/agent/zero-to-one/first-agent)包含 `pom.xml`、配置和全部 Java 文件。IDEA 导入 Maven 项目后会读取这些依赖，你不必逐个安装：

| 作用 | 已使用的依赖 |
|---|---|
| 模型协议适配 | `spring-ai-starter-model-openai`，通过 Spring AI BOM 统一版本 |
| HTTP 接口 | `spring-boot-starter-webmvc` |
| 请求校验 | `spring-boot-starter-validation` |

`application.yml` 的关键配置节选：

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      timeout: 120s
      max-retries: 0
      chat:
        completions-path: /chat/completions
        options:
          model: deepseek-flash
          max-tokens: 2048
          extra-body:
            thinking:
              type: disabled
```

`openai` 是 Spring AI 的协议适配配置名，不代表请求发给 OpenAI。`base-url` 和 `completions-path` 拼出 DeepSeek 的实际地址；显式设置路径，避免误用默认的 `/v1/chat/completions`。

`extra-body` 会将 `thinking` 放入请求 JSON 顶层，实际发送的是 `"thinking": {"type": "disabled"}`，不是一个叫 `extra_body` 的嵌套字段。[Spring AI OpenAI 配置](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)

完整配置还关闭了框架层重试，便于统计调用次数；`max-tokens` 只限制单次输出，不能代替整个任务的次数预算。HTTP 默认只监听 `127.0.0.1:8080`，教学日志单独设为 `INFO`，完整值见仓库配置文件。

## 3、把 Java 方法变成两个工具

`DocTools` 提供两个普通 Java 方法。`searchDocs(keyword)` 像查目录，只返回文档 ID 和标题；`readDoc(docId)` 像翻开其中一页，才会返回正文。例如先搜索“订单”得到 `order-api-v2`，再读取它，才能看到“分页规则在 `pagination-v2`”这句话。模型还要继续读 `pagination-v2`，才能得到具体参数。

下面节选 `readDoc`。`@Tool` 描述动作，`@ToolParam` 描述模型需要提供的参数；Java 仍要在方法入口校验模型生成的值：

```java
/**
 * 只接受固定集合中的精确文档 ID；不会将 ID 当成本地路径或 URL。
 * 格式错误和文档不存在都作为明确结果回给模型，便于它修正或停止。
 */
@Tool(description = "根据搜索结果或正文引用中的文档 ID 读取教学文档。")
public String readDoc(
        @ToolParam(description = "精确的文档 ID，例如 order-api-v2") String docId) {
    if (docId == null || !docId.matches("[a-z0-9-]{1,64}")) {
        String result = "INVALID_ARGUMENT: 文档 ID 格式不正确。";
        trace.accept("readDoc 返回：" + result);
        return result;
    }
    for (Doc doc : DOCS) {
        if (doc.id().equals(docId)) {
            trace.accept("readDoc: " + doc.id());
            String result = "来源：" + doc.id() + " / " + doc.title() + "\n正文：" + doc.content();
            // 仅因资料公开且固定才把正文放进返回轨迹；真实业务文档须按身份授权并收紧轨迹。
            trace.accept("readDoc 返回：" + result);
            return result;
        }
    }
    String result = "NOT_FOUND: 指定文档不存在。";
    trace.accept("readDoc 返回：" + result);
    return result;
}
```

`@Tool` 是给模型看的方法说明。模型可以提出“请调用 `readDoc`”，真正运行这个 Java 方法的是应用程序。看到工具请求，不等于已经读到了文档；还要看工具的返回值。[完整的 DocTools.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/tool/DocTools.java)包含标题搜索和两份固定教学资料。

这里有两个刻意保留的区别：

- 搜索只返回标题和 ID，读取才返回正文。因此，找到标题并不等于已经取得回答依据。
- “没找到”和“参数不合法”返回明确结果，模型可以据此调整查询或说明问题；它们不是一段空字符串。

`readDoc` 不接受真实文件路径，也没有访问项目目录的能力。这个固定集合不是多用户权限系统；接入真实文档时，搜索和读取都需要根据服务端可信身份过滤与鉴权。

为了让教学过程可观察，这两份**公开固定资料**的返回内容会写入本次请求的 `trace`，随 HTTP 响应返回；命令行练习通过 SLF4J 日志展示轨迹。真实项目文档不应照搬全文轨迹，应按权限处理响应与日志。

## 4、写出 Agent 的执行循环

### 4.1 先看我们要控制的过程

回想刚才查资料的过程：模型先说“帮我找订单文档”，Java 搜索后把结果交给它；模型再说“读这份文档”，Java 读完再交给它。模型拿到足够的正文后，才给出答案。代码就是重复做这几步：

```text
把问题发给模型 → 模型回答了吗？
  回答了：结束
  请求工具：Java 检查并执行 → 把结果交给模型 → 再判断一次
```

本例故意自己写这个循环，好让你看到是谁调用模型、是谁运行 Java 方法。所用 Spring AI 2.0 的 `ChatModel` 不会自动执行模型提出的工具请求；代码要显式调用 `ToolCallingManager`。以后也可以让 `ChatClient` 和 `ToolCallingAdvisor` 管理循环。[Spring AI 2.0 工具执行说明](https://docs.spring.io/spring-ai/reference/api/tools.html)

### 4.2 只看决定下一步的代码

HTTP 请求总是运行第 3 阶段。下面这段配置告诉 Spring AI：用哪个模型、这次能使用哪些 Java 工具。先看最后一行：每个用户请求都会创建自己的 `DocTools`，工具执行记录写入自己的 `trace`。

```java
var options = OpenAiChatOptions.builder()
        .model("deepseek-flash")
        .temperature(0.0)
        .maxTokens(2048)
        .extraBody(Map.of("thinking", Map.of("type", "disabled")))
        .toolCallbacks(ToolCallbacks.from(new DocTools(trace::add)))
        .build();
```

模型返回后，程序要判断：它已经回答，还是想使用工具？下面只保留这个关键分支。[完整 DocAgent.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java)还处理超时、空响应和异常：

```java
if (!response.hasToolCalls()) {
    String answer = output.getText();
    return answer == null || answer.isBlank()
            ? stopped("模型返回空答案。", trace)
            : completed(answer, trace);
}
var calls = output.getToolCalls();
// 最后一轮不给工具执行机会：执行后已没有模型轮次读取工具结果。
// 一次响应的全部工具请求先计入预算，不能只执行其中一部分。
if (round == MAX_MODEL_CALLS || calls.size() > MAX_TOOL_CALLS - toolCalls) {
    return stopped("剩余调用预算不足，尚未完成。", trace);
}
// 在任何工具执行前检查整批名称，模型请求不等于获得执行权限。
if (calls.stream().anyMatch(call -> !ALLOWED_TOOLS.contains(call.name()))) {
    return stopped("模型请求了未开放的工具。", trace);
}
toolCalls += calls.size();
for (var call : calls) {
    trace.add("请求工具：" + call.name() + "，调用 ID：" + call.id());
}
// Java 执行允许的工具；Manager 将工具请求、调用 ID 和结果一并写回历史。
// 下一轮必须用这份历史，模型才能基于刚读到的文档继续决策。
var result = manager.executeToolCalls(prompt, response);
prompt = new Prompt(result.conversationHistory(), options);
```

先看 `if (!response.hasToolCalls())`：没有工具请求，程序就取出文字并结束。有工具请求，程序先检查“次数够不够、工具准不准用”，再让 `ToolCallingManager` 执行。最后一行把本次工具结果装进下一次给模型的消息。

### 4.3 读懂最关键的几行

`ToolCallbacks.from(new DocTools(trace::add))` 把两个 Java 方法交给 Spring AI，作为模型可请求的工具；`trace::add` 记录这次请求里的执行过程。上面的 `OpenAiChatOptions` 还指定了 DeepSeek 模型、单次输出上限，并关闭思考模式。这里使用这个具体选项类型，是为了匹配本项目使用的 Spring AI OpenAI 适配器。

`model.call(prompt)` 负责把消息发给模型。有些模型响应会同时带文字和工具请求；只要有工具请求，程序就先处理工具，不把那段文字当最终答案。

`manager.executeToolCalls(prompt, response)` 才真正调用 Java 方法。`conversationHistory()` 保存了前面的对话、模型这次请求了哪个工具、工具返回了什么。把它交给下一次 `model.call`，模型才知道刚才查到了什么。

`prompt` 和计数器都是 `run` 的局部变量，每次任务重新创建。本例不会把上一个问题的历史带入下一个问题，也没有实现跨轮用户会话记忆。

### 4.4 为什么要有停止条件

例子限制最多 6 次模型调用和 8 次工具调用。两者分开计数，因为模型一次可能请求多个工具；最后一轮若仍要求工具，程序会停止，避免执行完却没有预算再取得答案。

5 分钟是轮次间检查的任务预算，**不是能强行中断所有阻塞操作的硬截止时间**。配置文件另设 OpenAI 兼容客户端的单次请求超时 120 秒；正在进行的请求可能越过任务预算，返回后才被判定超时。

Java 只允许 `searchDocs` 和 `readDoc`，还会检查参数。模型或工具出错时，程序返回停止原因，不编一个答案。HTTP 返回 `COMPLETED` 只表示模型给出了候选答案，仍要看文档是否支持它的说法。

### 4.5 这些“护栏”叫什么

除了循环，`DocAgent` 还管调用次数、超时和允许使用的工具；`DocTools` 检查工具参数。这些帮助模型完成任务、又防止它无限调用或随意执行的程序代码，常被叫作 **harness**。在本例中，模型提出“想读哪份文档”，Java 决定“能不能读、读完返回什么”。

这套护栏还不能自动判断自然语言答案是否有依据，也不能在程序重启后恢复中途的任务。所以响应写的是“候选答案”，需要对照实际读取的文档核查。接入真实业务资料时，还要按用户身份控制可读范围；若增加写操作，则要处理审批、幂等、事务和回滚。

## 5、让用户通过 HTTP 提问

练习时，你在 IDEA 控制台输入问题。做成接口后，用户在 HTTP 请求里输入问题。接口不会替用户预设问题，也不让用户指定教学阶段。

HTTP 请求长这样。`question` 是这次用户自己输入的问题：

```http
POST /api/agent/ask
Content-Type: application/json

{"question":"订单查询的分页参数怎么传？请给出文档依据。"}
```

```text
用户提交 question → Controller 接收 → DocAgent 处理 → 返回 answer 和 trace
```

### 5.1 定义入参和出参

你只需提交 `question`。代码用几个名字不同的对象传递它：`AskQuery` 接收并检查 HTTP 请求；`AskDTO` 把问题交给 Agent；`AgentResultDTO` 装处理结果；`AskVO` 把结果交还给用户。它们是程序不同位置使用的数据外壳，不是要你填写四次。空问题或超过 1000 字的问题会返回 HTTP 400。[Spring MVC 请求体验证](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)

| 字段 | 作用 |
|---|---|
| `question` | 用户本次提交的问题；不从代码中预设 |
| `status` | `COMPLETED` 表示得到候选答案，`STOPPED` 表示任务未完成 |
| `answer` | 候选答案或停止原因，需结合 `status` 解读 |
| `trace` | 本次请求的模型轮次与工具记录 |

这些对象的[完整声明与注释](https://github.com/tyronczt/hello-ai/tree/main/agent/zero-to-one/first-agent/src/main/java/example/agent)在仓库中。`COMPLETED` 不代表答案事实已自动核验；真实业务资料也不应直接把全文放进 `trace` 返回。

### 5.2 Controller 只做边界转换

`POST /api/agent/ask` 只接受 `question`。下面是 [AgentController.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/web/AgentController.java) 的入口方法：

```java
@PostMapping("/ask")
public AskVO ask(@Valid @RequestBody AskQuery query) {
    // 用户的问题来自本次 POST；stage 和用户身份都不从请求体传给 Agent。
    var result = agent.process(new AskDTO(query.question()));
    // STOPPED 仍有结构化响应，调用方需检查 status，不能把停止原因当成答案。
    return new AskVO(result.status().name(), result.answer(), result.trace());
}
```

这段方法只做两件事：接收用户问题，把 Agent 的结果交还给用户。查资料和决定何时停止，都在 `DocAgent` 里。这个示例没有登录和权限控制；以后接入真实文档时，搜索和读取都要按已认证的用户身份检查权限。

### 5.3 启动入口与教学流程分开

[AgentApplication.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/AgentApplication.java)看 IDEA 里填了什么启动参数，再决定启动哪种模式：

| Program arguments | 启动后做什么 |
|---|---|
| 留空 | 启动 HTTP 服务，等用户 POST 问题 |
| `--guided` | 打开四阶段练习，让你输入问题、预测、运行或跳过 |
| `--stage=2 "你的问题"` | 直接运行第 2 阶段，不经过预测和跳过 |

[DemoRunner.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/demo/DemoRunner.java)像练习的主持人：显示本轮会给模型什么，问你猜测和选择，然后打印结果。它自己不回答问题，也不提供模拟答案。选 `s` 时，它继续展示下一阶段，不调用模型；按回车时，它交给 `DocAgent`，由后者调用 DeepSeek。代码里这条路径是 `DemoRunner.show() → DocAgent.runStage() → model.call()`。

`DemoRunner` 实现了 `ApplicationRunner`，所以 Spring 启动后会调用它的 `run()`。教学模式里的 `WebApplicationType.NONE` 只表示不启动 HTTP 服务器；Spring 和 `DemoRunner` 仍会运行。只看预览可以暂时不填 API Key，但项目仍要保留 Spring AI 配置；真正按回车执行需要有效 Key。

主类放根包、其他类放子包，是为了让 Spring 能找到它们，符合 [Spring Boot 官方包结构建议](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)。完整目录和实现见仓库链接。

### 5.4 在 IDEA 启动 HTTP 服务

在 IDEA 中这样启动：

1. 打开 `first-agent/pom.xml`，选 JDK 21。
2. 创建主类为 `example.agent.AgentApplication` 的运行配置。
3. 在 **Environment variables（环境变量）** 中设置 `DEEPSEEK_API_KEY`。
4. **Program arguments（程序实参）留空**，点击运行。服务会等待 HTTP 请求。

不要勾选 **Store as project file / Share through VCS**，运行配置可能明文保存密钥。在 IDEA Terminal 中设置环境变量，也不会自动传给工具栏的 Run 配置。[JetBrains 环境变量说明](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)

启动只建立服务，不发送订单问题。保持 IDEA 中的服务运行，在 PowerShell 中发起请求：

```powershell
$body = @{ question = '订单查询的分页参数怎么传？请给出文档依据。' } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/agent/ask' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

把 `$body` 中的问题换成你的问题即可。下面只是响应形状示意，具体答案和工具顺序以实际返回为准：

```json
{
  "status": "COMPLETED",
  "answer": "pageNo 从 1 开始，pageSize 最大为 100；资料未规定默认值。来源：pagination-v2。",
  "trace": ["[模型调用 1/6]", "请求工具：readDoc，调用 ID：...", "readDoc 返回：来源：pagination-v2 / ..."]
}
```

参数不合法返回 HTTP 400。模型或工具中途停止时，HTTP 请求已被服务处理，返回 HTTP 200 与 `status: STOPPED`，调用方要检查 `status`，不能把 `answer` 中的停止原因当成问题答案。

### 5.5 多用户时状态放在哪里

`DocAgent` 是单例处理器，但每次 `process()` 都新建消息历史、工具实例、轮次计数与轨迹列表；共享的教学文档集合不可变。并发请求不会把甲的工具结果带进乙的下一轮。当前服务没有跨请求会话记忆，连续两次 POST 也是两个独立任务。

本例默认只监听本机，也没有登录、限流和用量配额。要给多设备、多用户使用，应先接入可信的鉴权入口，再开放监听地址；真实资料还要按身份过滤搜索和读取结果。`AGENT_PORT` 可以更改本机端口，`AGENT_BIND_ADDRESS` 可配置监听地址，但配置它不等于获得了鉴权能力。

## 6、怎样从输出判断它在做什么

以一次实际请求为例：你问“订单查询的分页参数怎么传？”，返回的 `trace` 记录了三次模型调用。`1/6` 表示“最多可调用模型 6 次，现在是第 1 次”，不是第 1 个教学阶段。按顺序看：

| 顺序 | 模型这次返回了什么 | Java 做了什么 | 下一次模型调用能看到什么 |
|---|---|---|---|
| 第 1 次模型调用 | 提出 **两次** `searchDocs` 请求 | 执行两次搜索，分别返回 `order-api-v2`、`pagination-v2` 的 ID 和标题 | 搜索结果；**还没有文档正文** |
| 第 2 次模型调用 | 根据搜索结果提出 **两次** `readDoc` 请求 | 读取两份固定教学文档，把正文作为工具结果回填 | 订单接口引用分页约定，以及 `pageNo`、`pageSize` 的具体规则 |
| 第 3 次模型调用 | 不再请求工具，返回文字答案 | 将文字放入 `answer`，以 `COMPLETED` 结束本次任务 | 本次任务结束 |

这次模型调用了 **3 次**，Java 工具执行了 **4 次**。因为一次模型响应可以提出多个工具请求，两个数字不必相等。

看到 `请求工具：...`，表示模型**想让 Java 做**这件事。看到 `searchDocs 返回：...` 或 `readDoc 返回：...`，才表示 Java 做完了。`调用 ID` 是配对工具请求和结果用的编号；`order-api-v2` 才是文档 ID。

在代码里，`model.call(prompt)` 把消息发给模型，`manager.executeToolCalls(prompt, response)` 执行模型请求的 Java 方法，并把结果放进下一次请求。第 3 次模型没有再请求工具，于是程序取出文字，返回 JSON。HTTP `200` 表示接口正常返回，`COMPLETED` 表示得到了候选答案；答案是否正确，仍要看读过的正文。

本例 `DocTools` 查询的是 Java 代码里固定的两份教学文档，没有查询真实订单、数据库或向量库。要核验这次答案，重点看 `readDoc 返回` 是否真的包含“`pageNo` 从 1 开始、`pageSize` 最大为 100、默认值未规定”。模型下次可能先读一份文档再读另一份，也可能一次请求多个工具；程序没有写死固定路径。

想在 IDEA 中亲眼看一遍，用 **Debug** 启动 HTTP 服务，发送同一个 POST，然后依次停在：

1. `AgentController.ask()`：看 `query.question()`，确认这是用户刚提交的问题。
2. `DocAgent` 中的 `model.call(prompt)`：看模型这轮是否请求工具；再停在 `manager.executeToolCalls(...)`，看 Java 何时执行。
3. `DocTools.searchDocs()` / `readDoc()`：看实际传入的参数和返回值。工具执行后，看 `result.conversationHistory()` 如何成为下一次调用的 `prompt`。

响应里的 `trace` 没记录搜索关键词，单凭截图不能反推出模型传了什么关键词。

要进一步观察反馈的作用，可以把订单文档正文改成完整的分页说明，再在 IDEA 中重新运行。模型可能直接结束；也可以保留引用但删掉公共约定，观察它能否说明资料缺失。

## 7、跑通以后，再做几项检查

| 检查 | 怎么操作 | 看什么 |
|---|---|---|
| 跨文档查询 | POST 提交“订单查询的分页参数怎么传？请给出文档依据。” | 是否读取相关正文，参数及来源是否正确 |
| 资料没写的值 | 询问 `pageSize` 默认值 | 应说明文档未规定，不能补出 10 或 20 |
| 超出资料范围 | 询问退款到账时间 | 应说明没有相应依据 |
| 文档缺失 | 临时移除公共约定，在 IDEA 中重新运行并提问 | 应报告缺失，不能假装读过 |
| 调用次数限制 | 临时将 `MAX_MODEL_CALLS` 改为 1 | 若模型请求工具，应在执行前停止 |
| 服务不可用 | 临时将 `DEEPSEEK_BASE_URL` 指向本机未监听的端口 | 应返回失败提示，不输出虚假的成功答案 |

测试完成后恢复教学数据、调用预算及服务地址。重复运行会产生新的线上用量，用少量固定问题检查是否有漏查或错误引用即可。

工具是否执行、次数限制是否生效，可以通过确定性测试验证；模型是否总能选择合适路径，需要真实模型上的重复评测。两类检查解决的问题不同。

### 7.1 想看得更明白，再做三组对照

**先比较阶段 1 和 2。** 两轮都有“只按资料回答”的规则，只有阶段 2 真正带了文档正文。看阶段 1 会不会在没资料时猜 `pageSize`；再看阶段 2 能不能从正文读出“最大 100、默认值未规定”。这能说明：加规则不能代替给资料。

**再比较阶段 2 和 3。** 阶段 2 一开始就把两份正文都发给模型。阶段 3 一开始只有工具说明；读到 `pagination-v2` 后，正文才进入下一次模型请求。看这一步，才能确认答案来自工具返回的资料。资料只有两份时，直接发送全文更简单；文档多了，再考虑检索。

**最后看工具结果有没有回到模型。** 模型提出 `readDoc` 请求后，Java 必须真的执行，并把正文放进下一次请求。重点看“模型请求 → Java 执行 → 下一轮看到结果”这三步。不要故意向线上接口发送不成对的工具消息；协议可能直接拒绝，测不到模型的回答能力。

这三组对照只验证输入和控制流的作用。要评估回答质量，固定题目及教学资料版本，分别记录“读到了哪些文档、答案哪句话由哪段正文支持、未规定字段是否被编造”，重复运行后再比较。对于真实项目，还应把权限拒绝、资料更新和服务超时加入样例集。

## 8、遇到问题或想继续扩展时再看

### 8.1 模型能聊天，却不调用工具

先确认使用的模型支持工具调用，再看工具是否随请求传入。工具描述也要明确，尤其是搜索返回标题、读取返回正文的区别。

如果程序没有 `readDoc` 执行记录，却输出了一份看似正确的参数说明，不算通过本篇的资料查询测试。模型可能根据常见分页习惯猜中了数值。

### 8.2 为什么搜索“订单 分页”找不到

本例只是标题包含匹配，不做分词或语义检索，因此工具说明要求使用一个简短关键词。失败时工具返回提示，模型可以改用“订单”或“分页”。

真实资料增多、表达方式变复杂后，再替换搜索实现。模型决策、工具执行和反馈这条主线可以保留。

### 8.3 一直查、查得慢，怎么排查

先看最后一条记录。如果停在模型调用，检查 DeepSeek 连接、账户可用额度及服务响应；401 通常先核对密钥，429 则检查限速，不要无限重试。若返回 400，核对接口路径、请求参数和工具历史；涉及 `reasoning_content` 时，先确认关闭思考的配置是否实际生效。如果同一工具反复出现，检查返回值是否补充了信息，以及文档引用是否缺失。

不要直接把调用次数调大。先用实际轨迹解释为什么需要下一次调用，再决定是否增加预算。

### 8.4 离真实项目还差什么

这个例子只处理固定教学文档，异常时通过 `status: STOPPED` 返回停止原因。HTTP 已提供结构化任务结果，但没有持久化恢复、自动核验引用、多用户鉴权或业务写操作。

后续可以按需求扩展：资料量变大时改进检索，多轮提问时引入会话记忆，对外提供服务时补充可信身份和任务管理。加入写操作前，还要落实业务状态校验、幂等与授权。

本篇先完成一件事：让模型能够选择 Java 工具，执行结果能够返回模型，并让这个过程有记录、能停止。

### 8.5 备选：迁移到本地模型

想试本地模型，可以参考 [Ollama 工具调用说明](https://docs.ollama.com/capabilities/tool-calling)另开分支。本例用了 DeepSeek 专属的 `thinking` 参数和 `OpenAiChatOptions`；换模型时，依赖、连接地址和这些选项都要一起调整，再检查工具调用记录。本篇没有验证 Ollama，不能只改 POM 和 YAML 就认为迁移完成。

### 8.6 下一篇：让 Spring AI 管理工具循环与多轮对话

到这里，我们已经能解释手写循环。按照[学习路线](README.md)的下一阶段，建议继续写《Javaer转Agent：让 Spring AI 管理工具循环与多轮对话》，仍使用同一个资料助手：

1. 用 `ChatClient` 及框架支持的工具执行机制替换手写循环，对照原有记录，确认由谁执行工具、怎样停止。
2. 连续提问“分页参数怎么传？”和“那它的默认值呢？”，观察第二次请求必须带上哪些历史。
3. 引入会话 ID，隔离不同会话的消息，区分一次任务内部的工具历史与用户多轮对话。
4. 加入“需要补充版本信息”的问题，用户回答后继续，并说明内存状态在重启后会丢失。
5. 复用本篇的检查用例，保留权限、次数与超时约束，不把框架托管等同于自动具备所有保护。

这一篇解决“从一次性任务走向可连续交互的助手”。随后再按问题扩展：文档增多时学习 RAG；工具需要跨应用复用时学习 MCP；出现明确分支、审批或重启恢复需求时，再学习状态管理与图编排。

## 9、参考资料与验证范围

四阶段对照借鉴了[《深入理解 AI Agent》第一章](https://bojieli.github.io/ai-agent-book/book/chapter1/)的实验思路，也参考了[Javaer 转 Agent 学习资料篇](https://tyron.me/posts/agent-resources)的选材方式。订单文档和 Java 程序是本篇重新设计的教学案例，下面的示意输出不代表原书的实验结果。

- [DeepSeek 首次调用 API](https://api-docs.deepseek.com/zh-cn/)：线上地址、模型名与认证方式。
- [DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)：开关及工具调用时的历史回传要求。
- [《深入理解 AI Agent》第一章](https://bojieli.github.io/ai-agent-book/book/chapter1/)：上下文组件对照实验与 Model / Harness 边界。
- [Javaer 转 Agent 学习资料篇](https://tyron.me/posts/agent-resources)：Java 主线与小型教学项目选材。
- [Spring AI 2.0.1 稳定版本](https://docs.spring.io/spring-ai/reference/spring-projects.html)与[升级说明](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)：版本和工具循环迁移。
- [Spring AI 2.0 工具调用](https://docs.spring.io/spring-ai/reference/api/tools.html)：工具注册、手动循环与消息回填。
- [Spring AI OpenAI 兼容配置](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)：请求超时与额外请求字段。
- [Ollama 工具调用](https://docs.ollama.com/capabilities/tool-calling)：待单独验证的本地模型迁移入口。

此前已在 JDK 21、Maven 3.9.11 下完成编译和打包。本机模拟 DeepSeek 接口检查了请求地址、认证头、模型名、思考模式开关和输出上限，也检查了跨文档结果回填、未开放工具、重复调用、文档缺失、工具超额、非法参数和空回答。HTTP 层检查了空问题返回 400、POST 返回答案和轨迹，以及三个并发问题不会串到一起；第 0～2 阶段的请求内容也用本机模拟服务核对过。

上面是**本机模拟验证**。此外，读者提供的一次真实 DeepSeek HTTP 调用返回了 `COMPLETED`，轨迹中有 3 次模型调用和 4 次工具执行，第 6 节据此讲解。这只说明该次请求跑通；模型换个问题或重跑一次，选工具的路径可能不同。Ollama 路径尚未验证。
