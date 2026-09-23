# Javaer转Agent：用 Java 做第一个 Agent

上一篇[《什么是 Agent》](01-what-is-agent.md)讲了一个资料助手：它搜索订单文档，发现正文引用公共分页约定，再读取约定，最后给出答案。这一篇把这个过程写成 Java 程序。

[完整代码：GitHub / agent/zero-to-one](https://github.com/tyronczt/hello-ai/tree/main/agent/zero-to-one)。本文只展示理解机制所需的关键代码，完整项目、配置与注释以仓库为准。

最终程序是一个 HTTP 服务。用户把问题放进 `POST /api/agent/ask`，Controller 接收参数，`DocAgent` 作为处理器查询资料并返回答案及轨迹；服务启动时不预设任何问题。为了理解它内部怎样工作，仍可选用命令行的四阶段引导练习：只调用模型、增加任务规则、把资料放进上下文、让模型按需读取资料。

本篇借用[《深入理解 AI Agent》第一章](https://bojieli.github.io/ai-agent-book/book/chapter1/)的“改变上下文组件再观察行为”的实验思路，以及[学习资料篇](https://tyron.me/posts/agent-resources)中“小型教学实现优先、用 Java 对照机制”的选材方式。下面的订单文档、参数和程序都是**本篇重新设计的 Java 教学案例**，不是原书实验的 Java 翻译，也不引用原书的实验结果当作本程序的结果。

最终调用从用户输入开始，`question` 的值由每次请求决定：

```http
POST /api/agent/ask
Content-Type: application/json

{"question":"订单查询的分页参数怎么传？请给出文档依据。"}
```

`Controller → Query 转 DTO → DocAgent → DocTools → 结果 DTO 转 VO → JSON 响应`。不同用户的每次调用有各自的消息历史、预算和工具轨迹；本篇没有跨请求记忆。第 5 节给出可复制的启动和请求命令。

## 0、先用同一个问题做四次迭代

在 IntelliJ IDEA 中打开 `first-agent/pom.xml`，选择 JDK 21，创建主类为 `example.agent.AgentApplication` 的运行配置。在 **Environment variables** 中设置 `DEEPSEEK_API_KEY`，将 **Program arguments** 设为 `--guided`，点击运行即可进入引导练习；问题由你在 IDEA 的 Run 控制台输入。密钥配置注意事项见第 5.4 节。

程序先请你输入一个问题；空输入不会执行。四轮都用你输入的同一个问题，才能比较变化。每轮会展示将发送的初始消息和工具摘要，请你写下预测；再按回车运行、输入 `s` 跳过，或输入 `q` 退出。运行后看实际回答与工具轨迹，并写一句发现。**只有按回车运行才会请求线上模型并产生用量。**

交互大致这样进行；`<实际输出>` 每次由你运行的模型生成，文章不预填答案：

```text
=== 阶段 1 增加任务规则 ===
本轮模型收到：
[system] ...只依据实际读到的正文回答...
[user] 订单查询的分页参数怎么传？请给出文档依据。
你预测它会怎样回答或行动？
> 我猜它会说没有资料，也可能直接猜一个分页值
回车运行；输入 s 跳过本阶段；输入 q 退出：
<此时由你决定是否发送请求>
实际执行：
<实际输出>
对照重点：规则要求引用，但本轮没有正文或工具...
```

例如第 1 阶段，屏幕会先显示系统规则和用户问题，但没有文档正文。你可以先猜“模型会承认没有依据，还是会给出一个常见分页值”，再决定是否运行。第 3 阶段还会显示 `searchDocs` / `readDoc` 的请求与固定教学资料返回值，方便检查模型下一轮究竟依据了什么。程序展示的是实际构造的初始消息与便于阅读的工具摘要，不是原始 HTTP JSON；第 3 阶段的后续消息要结合工具轨迹观察。

四轮使用同一个模型。变化的是每轮给它的信息和可用动作：

| 阶段 | 这次新增什么 | 模型能看到 / 能做什么 | 观察重点 |
|---|---|---|---|
| 0：裸问题 | 只传 `UserMessage` | 看到用户问题；没有项目资料和工具 | 回答即使听起来合理，也没有项目依据 |
| 1：任务规则 | 再传 `SystemMessage` | 知道“要引用资料、未知就说未知”；仍无资料 | 提示词能规定行为，不能凭空提供事实 |
| 2：资料进入上下文 | 把两份完整教学文档随问题传入 | 一次调用就能读到两份正文 | 对少量资料有效；请求随资料量增长，每轮都要重复发送 |
| 3：按需读取 | 只给 `searchDocs`、`readDoc` 定义；Java 执行并回填结果 | 模型选择查什么，第二轮根据结果继续 | 区分“模型请求调用”和“工具已由 Java 执行” |

在代码里，0～2 阶段的差别可以压缩成下面几行。`DocTools.demoContext()` 只是把本篇固定文档拼成文本；第 2 阶段还没有检索，更谈不上向量数据库。引导界面和真正的模型调用复用同一个 `initialMessages`，所以预览与实际构造的初始消息一致。

```java
public static List<Message> initialMessages(String question, int stage) {
    if (stage == 0) {
        return List.of(new UserMessage(question));
    }
    return List.of(new SystemMessage(SYSTEM), new UserMessage(stage == 2
            ? "教学资料：\n" + DocTools.demoContext() + "\n问题：" + question : question));
}
```

这里要分清三种“记住”：模型训练得到的通用知识不等于项目资料；本轮请求携带的文档是**上下文**；把用户偏好或历史跨请求保存，才涉及**会话记忆**。第 2 阶段的两份文档可以视作一个极小的教学知识集合，但知识库本身在 Java 内存里，模型只看见程序放进请求的内容。将来资料增多，可替换搜索实现为数据库、全文检索或向量检索；那是检索方法升级，模型仍只能依据实际返回的材料判断。

第 3 阶段则把“全部预先给出”改成“需要时再取”。搜索只返回 ID 和标题，读取才有正文；订单文档又引用公共分页约定，因此一次工具结果可能改变下一步选择。工具定义是模型的**可选动作说明**，不是资料正文，也不代表已经执行。真正执行发生在 `ToolCallingManager.executeToolCalls(...)`，执行结果与调用 ID 一起进入下一轮历史。第 3、4 节会解释相关关键代码。

四个阶段的判断不能只看答案是否碰巧正确。第 0、1 阶段如果说出 `pageSize=100`，也没有依据；第 2 阶段应能从完整资料中指出最大值，并说明默认值未规定；第 3 阶段还要在轨迹中看到 `readDoc: pagination-v2`。真实模型的路径可能不同，所以请记录实际轨迹，不把下文示意输出当作固定答案。

需要复查某一轮时，把 IDEA 运行配置的 **Program arguments** 改为 `--stage=2 "你的问题"`，重新运行。清空该参数后启动 HTTP 服务，不会自动提问。

### Harness 从哪里开始

第 3 阶段里，`DocAgent` 不只是一个 `while` 循环。它负责构造本轮上下文、声明可用工具、限制调用次数和耗时、拒绝未开放工具，并在失败时停止；`DocTools` 在参数入口做检查。这些围绕模型的运行与治理代码，就是这个示例的最小 harness。模型决定“试着读哪份文档”，Java 决定“这次读取能否执行”。

这个 harness 有边界：它没有自动核验自然语言答案是否被原文支持，也没有失败后的持久化恢复。这里把最终输出标作“候选答案”，并要求人工核对工具记录与文档；不能把调用成功当作事实正确。后续要接真实业务数据时，搜索与读取都要按可信用户身份过滤，涉及写操作还要增加审批、幂等、事务和可回滚的验证步骤。

## 1、先明确要做出的效果

用户输入：

> 订单查询的分页参数怎么传？请给出文档依据。

教学资料只有两份：

| 文档 ID | 标题 | 正文要点 |
|---|---|---|
| `order-api-v2` | 订单查询接口 v2 | 分页规则见 `pagination-v2` |
| `pagination-v2` | 公共分页约定 v2 | `pageNo` 从 1 开始，`pageSize` 最大为 100，未规定默认值 |

这些参数是本篇设定，不是通用规范。第 2 阶段会把正文直接放进初始消息；第 3 阶段不放，模型需要通过工具获取。

程序提供两个动作：`searchDocs` 根据关键词返回文档 ID 和标题，`readDoc` 读取指定正文。至于先查什么、读到引用后是否继续查，由模型根据结果选择。

本篇使用固定、公开的教学数据，没有文件写入和业务操作。HTTP 请求可以由多个用户同时发起；真实资料的权限、身份和会话记忆仍需另行实现。

## 2、准备环境和项目

### 2.1 本篇使用的技术组合

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

在 [DeepSeek 开放平台](https://platform.deepseek.com/)创建 API Key，确认账户可调用 API。线上请求会产生用量，先使用本文两份教学资料，密钥通过环境变量提供，不写进代码或仓库。

在 IDEA 的 `example.agent.AgentApplication` 运行配置中，将 Key 填入 **Environment variables** 的 `DEEPSEEK_API_KEY`，具体操作见第 5.4 节。不要打印密钥，也不要把它写进代码、日志或共享运行配置。这里不配置本地模型，Ollama 的安装与切换放在备选小节。

### 2.3 建立一个独立的 Maven 项目

新建 `first-agent` 目录。启动入口放在根包，Agent、工具、命令行演示和 Web 边界分别放在子包：

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

[GitHub 上的完整 Maven 项目](https://github.com/tyronczt/hello-ai/tree/main/agent/zero-to-one/first-agent)包含 `pom.xml`、配置和全部 Java 文件。这里先看选型，再看与 Agent 行为直接有关的配置：

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

`DocTools` 提供 `searchDocs(keyword)` 和 `readDoc(docId)`。前者只按标题查找，返回文档 ID 与标题；后者根据精确 ID 返回正文。这样模型必须先取得正文，才能把它当作回答依据。

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

工具方法本身仍是普通 Java 方法。模型发出工具请求后，由应用执行这些方法；单有 `@Tool` 不表示文档已经被读取。[完整的 DocTools.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/tool/DocTools.java)还包含标题搜索和两份固定教学资料。

这里有两个刻意保留的区别：

- 搜索只返回标题和 ID，读取才返回正文。因此，找到标题并不等于已经取得回答依据。
- “没找到”和“参数不合法”返回明确结果，模型可以据此调整查询或说明问题；它们不是一段空字符串。

`readDoc` 不接受真实文件路径，也没有访问项目目录的能力。这个固定集合不是多用户权限系统；接入真实文档时，搜索和读取都需要根据服务端可信身份过滤与鉴权。

为了让教学过程可观察，这两份**公开固定资料**的返回内容会写入本次请求的 `trace`，随 HTTP 响应返回；命令行练习通过 SLF4J 日志展示轨迹。真实项目文档不应照搬全文轨迹，应按权限处理响应与日志。

## 4、写出 Agent 的执行循环

### 4.1 先看我们要控制的过程

每一轮只做四件事：调用模型；判断它是回答还是请求工具；执行允许的工具；把带有执行结果的历史交回模型。

Spring AI 2.0 可以通过 `ChatClient` 和 `ToolCallingAdvisor` 代管循环。本篇为了看清过程，直接调用 `ChatModel`，由自己的代码控制循环。2.0 已移除 `ChatModel` 的内部工具执行开关；模型返回工具请求时，不会自动执行 Java 方法。参数解析和工具结果消息的组织仍交给 `ToolCallingManager`。[Spring AI 2.0 工具执行说明](https://docs.spring.io/spring-ai/reference/api/tools.html)

### 4.2 只看决定下一步的代码

HTTP 请求会让 `DocAgent` 运行第 3 阶段。先构造本轮选项：显式关闭 DeepSeek 思考模式，并为**本次请求**创建带有独立轨迹接收器的工具实例。

```java
var options = OpenAiChatOptions.builder()
        .model("deepseek-flash")
        .temperature(0.0)
        .maxTokens(2048)
        .extraBody(Map.of("thinking", Map.of("type", "disabled")))
        .toolCallbacks(ToolCallbacks.from(new DocTools(trace::add)))
        .build();
```

再看模型返回后的分支。下面是[完整 DocAgent.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java)的连续节选；调用前后的超时检查、空响应处理及异常处理仍在源码中：

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

模型没有请求工具时，本轮文字成为候选答案；请求了工具时，先校验本轮预算和允许的名称，再由 `ToolCallingManager` 执行。执行后的历史包含工具调用 ID 和结果，下一轮模型才能据此继续判断。

### 4.3 读懂最关键的几行

`ToolCallbacks.from(new DocTools(trace::add))` 把两个方法转换成工具定义及其执行回调，工具轨迹写入本次请求的列表。提示词告诉模型该如何完成任务，工具定义告诉它有哪些动作可选。`OpenAiChatOptions` 是 2.0 版 OpenAI 兼容模型的具体选项类型；这里显式填写模型、输出上限和 `thinking`，因为传入本轮选项时不能假定配置文件中的同名默认值会完整保留。通用 `ToolCallingChatOptions` 在此版本的适配器中会触发运行时类型错误。

`model.call(prompt)` 返回后，先判断是否存在工具调用。不能因为响应里有文字就立即结束，有些响应可能同时包含文字和工具请求。

`manager.executeToolCalls(prompt, response)` 才会真正调用 Java 方法。随后的 `conversationHistory()` 包含原有消息、本轮工具请求及匹配的执行结果。下一轮保留这些历史，模型才能知道刚才查到了什么；只发工具返回的字符串会丢失对话和调用关系。

`prompt` 和计数器都是 `run` 的局部变量，每次任务重新创建。本例不会把上一个问题的历史带入下一个问题，也没有实现跨轮用户会话记忆。

### 4.4 为什么要有停止条件

例子限制最多 6 次模型调用和 8 次工具调用。两者分开计数，因为模型一次可能请求多个工具；最后一轮若仍要求工具，程序会停止，避免执行完却没有预算再取得答案。

5 分钟是轮次间检查的任务预算，**不是能强行中断所有阻塞操作的硬截止时间**。配置文件另设 OpenAI 兼容客户端的单次请求超时 120 秒；正在进行的请求可能越过任务预算，返回后才被判定超时。

工具只开放两个名称，参数还要在方法入口校验。出现传输或执行异常时，程序终止本次任务，不把错误伪装成答案。HTTP 返回 `COMPLETED` 只表示模型给出候选答案，正确性仍要结合文档和调用记录检查。

## 5、让用户通过 HTTP 提问

命令行阶段用于看清 Agent 的内部机制。真正调用时，用户在请求体里提交问题；Controller 不预设问题，也不向模型暴露教学用的 `stage` 参数。

```text
HTTP POST → AskQuery → AskDTO → DocAgent → AgentResultDTO → AskVO → JSON
```

### 5.1 定义入参和出参

Web 边界用 `AskQuery` 接收本次问题，`@Valid` 配合 `@NotBlank`、`@Size(max = 1000)` 拦截空问题和过长问题，返回 HTTP 400。Controller 转成应用内部的 `AskDTO`；`DocAgent` 返回 `AgentResultDTO`；最后转成给调用方的 `AskVO`。[Spring MVC 请求体验证](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)

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

Controller 校验请求并转换对象，`DocAgent` 负责工具循环和停止条件。用户身份不能靠请求体里的 `userId` 自称；本例没有认证与租户权限。接入真实文档时，应从可信的认证上下文取得身份，并在搜索和读取两处检查权限。

### 5.3 启动入口与教学流程分开

[AgentApplication.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/AgentApplication.java)只启动 Spring：不带教学参数时提供 HTTP 服务；显式传 `--guided` 或 `--stage=0..3` 时选择非 Web 模式。[DemoRunner.java](https://github.com/tyronczt/hello-ai/blob/main/agent/zero-to-one/first-agent/src/main/java/example/agent/demo/DemoRunner.java)负责读取问题、展示阶段、调用 Agent 和记录 SLF4J 日志；服务启动本身不会向模型提问。

Spring Boot [官方包结构建议](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)是将主类放在根包，供组件扫描覆盖子包；Spring AI 的[官方 Java 示例仓库](https://github.com/spring-projects/spring-ai-examples)也按独立示例组织启动类和功能代码。本例把 `service`、`tool`、`demo` 分开，完整实现见上面的仓库链接。

`application.yml` 的 120 秒是单次模型请求超时，不等于 Agent 的 5 分钟轮次预算。正在进行的请求可能越过任务预算，返回后才被判定超时。[官方连接属性](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)

### 5.4 在 IDEA 启动 HTTP 服务

在 IntelliJ IDEA 中打开 `first-agent/pom.xml`，选择 JDK 21，新建 `example.agent.AgentApplication` 运行配置。把真实 Key 填在 **Environment variables** 的 `DEEPSEEK_API_KEY` 中，**Program arguments 留空**，运行后服务持续监听。不要勾选 **Store as project file / Share through VCS**，因为运行配置可能明文保存密钥。IDEA Terminal 中设置的环境变量不会自动进入工具栏 Run 配置。[JetBrains 环境变量说明](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)

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

下面是一条可能的 HTTP 响应示意，不是对真实模型输出的保证。`trace` 的顺序就是该请求内实际发生的顺序：

```json
{
  "status": "COMPLETED",
  "answer": "pageNo 从 1 开始，pageSize 最大为 100。资料没有规定 pageSize 的默认值。来源：order-api-v2、pagination-v2。",
  "trace": [
    "[模型调用 1/6]",
    "请求工具：searchDocs，调用 ID：...",
    "searchDocs: 命中 1 份文档",
    "[模型调用 2/6]",
    "请求工具：readDoc，调用 ID：...",
    "readDoc: order-api-v2",
    "readDoc 返回：来源：order-api-v2 / 订单查询接口 v2\n正文：订单查询支持分页，具体参数遵循《公共分页约定 v2》，文档 ID 为 pagination-v2。",
    "[模型调用 3/6]",
    "请求工具：readDoc，调用 ID：...",
    "readDoc: pagination-v2",
    "readDoc 返回：来源：pagination-v2 / 公共分页约定 v2\n正文：pageNo 从 1 开始；pageSize 最大为 100。本文未规定 pageSize 的默认值。",
    "[模型调用 4/6]"
  ]
}
```

关注 `readDoc` 的实际记录，而不只是答案里声称的来源。模型可能换关键词、一次请求多个工具，或直接搜索分页文档，路径不必逐行一致。判断重点是它是否读到了支持答案的正文。

代码没有写死“先读订单接口，再读分页约定”。第二次读取来自第一份文档里的引用，并由模型选择，这正是本例中的 Agent 行为。

要进一步观察反馈的作用，可以把订单文档正文改成完整的分页说明，再在 IDEA 中重新运行。模型可能直接结束；也可以保留引用但删掉公共约定，观察它能否说明资料缺失。

## 7、跑通以后，做几项检查

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

### 7.1 做三组有解释力的对照

**先比较阶段 1 和 2。** 两次请求都有“只依据实际资料”的规则，只有阶段 2 附带了正文。如果阶段 1 给出一个确定的 `pageSize`，检查它是否承认自己没有资料；若没有，这暴露的是事实来源不足，而不是需要再写一句更强硬的提示词。阶段 2 应从文档读出“最大 100、默认值未规定”。

**再比较阶段 2 和 3。** 两者拥有同样两份资料，但阶段 2 在每次请求里放入全文，阶段 3 初始请求只有工具定义。观察第 3 阶段第一次请求的 `messages` 不含分页规则正文，读到 `pagination-v2` 后的下一次请求才出现该正文。这样才能证明知识是按需取得的，而非模型原本就知道。资料量很小时，第 2 阶段反而更简单；等文档多、版本变动频繁或有访问控制时，检索才有明显价值。

**最后比较有无工具结果。** 保留 `searchDocs` 和 `readDoc` 定义，但如果 Java 不执行 `executeToolCalls`，模型最多只能提出读取请求；它不会自动拥有文档内容。实际程序会把工具结果和对应调用 ID 一并放回历史。观察“模型请求 → Java 执行 → 下一轮消息中出现结果”这三个节点，比只看到最终答案更能说明 Agent 的闭环。不要故意构造不成对的工具消息发给线上接口；那可能被协议校验拒绝，不能用来测模型推理能力。

这三组对照只验证输入和控制流的作用。要评估回答质量，固定题目及教学资料版本，分别记录“读到了哪些文档、答案哪句话由哪段正文支持、未规定字段是否被编造”，重复运行后再比较。对于真实项目，还应把权限拒绝、资料更新和服务超时加入样例集。

## 8、常见问题与本例的边界

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

如果希望练习本地模型，可以参考 [Ollama 工具调用说明](https://docs.ollama.com/capabilities/tool-calling)另开一个分支试验。Spring AI 2.0.1 的本例在第 3 阶段使用 `OpenAiChatOptions` 承载 DeepSeek 的 `thinking` 扩展参数；迁移时需要同时替换模型 Starter、连接配置和模型专用选项，并重跑本篇的工具轨迹检查。只改 POM 和 YAML、保持三个 Java 类不动，不能视为可运行的 Ollama 版本。本篇没有验证 Ollama 路径。

### 8.6 下一篇：让 Spring AI 管理工具循环与多轮对话

到这里，我们已经能解释手写循环。按照[学习路线](README.md)的下一阶段，建议继续写《Javaer转Agent：让 Spring AI 管理工具循环与多轮对话》，仍使用同一个资料助手：

1. 用 `ChatClient` 及框架支持的工具执行机制替换手写循环，对照原有记录，确认由谁执行工具、怎样停止。
2. 连续提问“分页参数怎么传？”和“那它的默认值呢？”，观察第二次请求必须带上哪些历史。
3. 引入会话 ID，隔离不同会话的消息，区分一次任务内部的工具历史与用户多轮对话。
4. 加入“需要补充版本信息”的问题，用户回答后继续，并说明内存状态在重启后会丢失。
5. 复用本篇的检查用例，保留权限、次数与超时约束，不把框架托管等同于自动具备所有保护。

这一篇解决“从一次性任务走向可连续交互的助手”。随后再按问题扩展：文档增多时学习 RAG；工具需要跨应用复用时学习 MCP；出现明确分支、审批或重启恢复需求时，再学习状态管理与图编排。

## 9、参考资料与验证范围

- [DeepSeek 首次调用 API](https://api-docs.deepseek.com/zh-cn/)：线上地址、模型名与认证方式。
- [DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)：开关及工具调用时的历史回传要求。
- [《深入理解 AI Agent》第一章](https://bojieli.github.io/ai-agent-book/book/chapter1/)：上下文组件对照实验与 Model / Harness 边界。
- [Javaer 转 Agent 学习资料篇](https://tyron.me/posts/agent-resources)：Java 主线与小型教学项目选材。
- [Spring AI 2.0.1 稳定版本](https://docs.spring.io/spring-ai/reference/spring-projects.html)与[升级说明](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)：版本和工具循环迁移。
- [Spring AI 2.0 工具调用](https://docs.spring.io/spring-ai/reference/api/tools.html)：工具注册、手动循环与消息回填。
- [Spring AI OpenAI 兼容配置](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)：请求超时与额外请求字段。
- [Ollama 工具调用](https://docs.ollama.com/capabilities/tool-calling)：待单独验证的本地模型迁移入口。

主线示例已在 JDK 21、Maven 3.9.11 下编译打包通过。本机模拟 DeepSeek 接口验证了实际请求路径、认证头、`deepseek-flash` 模型名、关闭思考参数、输出上限，以及跨文档结果回填、未开放工具、重复调用、资料缺失、工具超额、非法参数、空回答七类场景。HTTP 层另外验证了空问题返回 400、POST 问题返回结构化答案与轨迹，以及三个并发问题的答案和轨迹互不混入。第 0～2 阶段的请求组成另以本地模拟服务检查。尚未执行真实 DeepSeek 线上推理或本地模型推理；模拟验证不代表模型选择工具和答案质量已获证实。
