# AgentController.ask 调用逻辑

本图基于当前工作区源码静态阅读，入口是 `POST /api/agent/ask`，只展开 HTTP 固定使用的 `stage=3`。没有运行线上模型；工具请求的名称、参数、顺序和轮次取决于实际模型响应。

主调用链：`AgentController.ask → DocAgent.process → run(question, 3) → execute → model.call / manager.executeToolCalls → AgentResultDTO → AskVO`。

## 调用流程图

```mermaid
flowchart TD
    A["POST /api/agent/ask"] --> B{"请求体及 Bean Validation 是否通过？"}
    B -->|否| BAD["Web 层拒绝请求，不进入 ask"]
    B -->|是| C["AgentController.ask：AskQuery 转 AskDTO"]
    C --> D["DocAgent.process → run：固定 stage=3，创建 taskId，记录日志"]
    D --> E["execute：创建本次 trace，校验 question 和 stage"]
    E -->|无效| STOP["stopped：STOPPED，answer 为停止原因"]
    E -->|有效| F["注册 DocTools 回调；initialMessages 构造规则和问题；创建 Prompt"]
    F --> G["初始化计时、toolCalls=0、round=1"]
    G --> H{"调用前是否达到 5 分钟？"}
    H -->|是| STOP
    H -->|否| I["记录模型轮次；model.call(prompt)"]
    I --> J{"调用后超时，或 response/result 为空？"}
    J -->|是| STOP
    J -->|否| K{"response.hasToolCalls？"}
    K -->|否| L{"答案文字非空？"}
    L -->|否| STOP
    L -->|是| OK["completed：COMPLETED，answer 为候选答案"]
    K -->|是| M{"round=6，或本批工具数超过剩余 8 次预算？"}
    M -->|是| STOP
    M -->|否| N{"整批工具名均为 searchDocs / readDoc？"}
    N -->|否| STOP
    N -->|是| O["累计工具数；记录名称和调用 ID"]
    O --> P["manager.executeToolCalls(prompt, response)"]
    P --> Q["框架按请求调度 DocTools：参数校验、搜索或读取、写入 trace"]
    Q --> R["工具结果回填 conversationHistory；重建 Prompt；round 加 1"]
    R --> H
    I -.->|循环内 RuntimeException| STOP
    P -.->|循环内 RuntimeException| STOP
    F -.->|初始化 RuntimeException| ERR["run 记录异常后重新抛出；不生成 AskVO"]
    OK --> S["AgentResultDTO：复制 trace 为不可变快照"]
    STOP --> S
    S --> T["run 记录完成或停止日志，返回 Controller"]
    T --> U["new AskVO(status.name(), answer, trace)"]
    U --> V["HTTP 响应：status / answer / trace"]
```

图中异常虚线标出主要入口；`execute` 的循环 `try` 内其他 `RuntimeException` 同样进入停止分支。初始化阶段位于该 `try` 外，其异常由 `run` 记录并重新抛出。`output` 若为空而触发运行时异常，也走循环异常分支。

## 关键交互时序图

下图描述参数有效时的交互；`break` 表示退出本次处理。工具批次可能请求任意一个获准工具，也可能请求多个工具；图中没有预设“先搜索、再读某份文档”的实际轨迹。

```mermaid
sequenceDiagram
    autonumber
    actor U as 调用方
    participant C as AgentController
    participant A as DocAgent
    participant M as ChatModel / 模型服务
    participant T as ToolCallingManager
    participant D as DocTools
    U->>C: POST /api/agent/ask，question
    Note over U,C: Spring MVC 解析请求并完成 Bean Validation
    C->>A: process(new AskDTO(question))
    A->>A: run(question, 3) → execute
    A->>D: new DocTools(trace 回调)
    A->>A: ToolCallbacks.from；initialMessages；new Prompt
    Note over A,M: 初始上下文为系统规则和问题；工具定义随 options 提供
    loop 模型轮次 round=1..6，完成或停止时退出
        A->>A: expired(started)；记录模型轮次
        break 调用前达到时间预算
            A->>A: stopped(reason, trace)
        end
        A->>M: model.call(prompt)
        M-->>A: ChatResponse：文字和/或 tool calls
        break 调用后超时或响应无效
            A->>A: stopped(reason, trace)
        end
        break 无工具请求
            A->>A: 非空文字 → completed；空文字 → stopped
        end
        A->>A: 检查剩余轮次、工具数量预算、整批工具白名单
        break 校验不通过
            A->>A: stopped(reason, trace)
        end
        A->>A: 累计 toolCalls，记录工具名称和调用 ID
        A->>T: executeToolCalls(prompt, response)
        Note over T,D: 通过注册回调调度本批工具，具体顺序不在此预设
        T->>D: searchDocs(keyword) 或 readDoc(docId)
        D->>D: 校验参数；查询固定 DOCS 集合
        D->>A: trace.accept → addTrace
        D-->>T: ID 和标题 / 正文 / INVALID_ARGUMENT / NOT_FOUND
        T-->>A: ToolExecutionResult，包含 conversationHistory
        A->>A: new Prompt(conversationHistory, options)
    end
    Note over A,T: 循环内 RuntimeException → 记录异常类型并 stopped
    A-->>C: AgentResultDTO(status, answer, trace)
    C-->>U: AskVO(status.name(), answer, trace)
```

## 对照源码阅读

| 阅读顺序 | 源码位置 | 重点 |
|---|---|---|
| 1 | [AgentController.ask](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/web/AgentController.java:25) | 只做 Query → DTO 和结果 → VO 转换 |
| 2 | [AskQuery](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/web/query/AskQuery.java:11) | question 非空白，最长 1000 字符 |
| 3 | [DocAgent.process](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java:54) | 固定进入 stage=3；HTTP 无法指定其他阶段 |
| 4 | [DocAgent.run](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java:72) | taskId、耗时、完成/停止日志、初始化异常重抛 |
| 5 | [DocAgent.execute](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java:100) | 输入校验、工具注册和全部循环逻辑 |
| 6 | [模型调用与响应判断](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java:158) | 是否继续取决于 hasToolCalls；同时返回文字和工具请求时优先处理工具 |
| 7 | [工具校验、执行与历史回填](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/service/DocAgent.java:184) | 先整批检查，再执行；下一轮必须使用更新后的历史 |
| 8 | [DocTools.searchDocs](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/tool/DocTools.java:42) | 仅按标题包含关系匹配，返回 ID 和标题，不返回正文 |
| 9 | [DocTools.readDoc](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/tool/DocTools.java:68) | 按精确 ID 读取固定资料；不是文件系统或数据库读取 |
| 10 | [AgentResultDTO](C:/Users/xiaotian/Documents/github/hello-ai/agent/zero-to-one/first-agent/src/main/java/example/agent/dto/AgentResultDTO.java:12) | COMPLETED / STOPPED；trace 复制为不可变快照 |

几个容易看错的地方：

- **循环发生在本次请求内部。** 每次 POST 都重新创建消息、工具实例、预算和轨迹，没有跨请求对话记忆。
- **模型返回的是工具请求。** Java 检查白名单和预算后，由 Manager 通过回调执行 `DocTools`。源码里没有 `execute → searchDocs` 的普通直接调用语句，因此静态调用图需结合工具注册关系阅读。
- **两种回填作用不同。** `conversationHistory` 供下一轮模型使用；`trace` 供调用方查看执行记录，工具输出会写入其中。
- **最多 6 次 `model.call`、8 次工具请求。** 第 6 轮如果还请求工具，会直接停止，因为已无后续模型轮次读取工具结果。一次响应的工具请求按整批计入预算。
- **5 分钟预算在模型调用前后检查。** 它不会主动中断阻塞中的调用，也不是独立定时取消器；工具批次结束后，到下一轮开始才再次检查。
- **工具参数错误或未找到资料不等于立即停止。** 工具返回 `INVALID_ARGUMENT` / `NOT_FOUND` 字符串，回填后模型仍可修正或给出回答。
- **COMPLETED 只表示得到非空候选答案。** 代码不强制至少调用一次工具，也不自动核验事实。STOPPED 的 `answer` 字段存放停止原因。

本项目 `pom.xml` 指定 Spring AI 2.0.1。Spring AI 2.0 已移除 `ChatModel` 内部工具执行循环；这里直接使用 `ChatModel` 和 `ToolCallingManager` 的手动循环与该机制一致。依据：[Spring AI 官方升级说明](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)。

## GitHub skill / 项目选择

以下是仓库文档核验后的建议，未安装或实测这些第三方工具。第三方模板的版式和工作流未套用到本项目。

| 项目 | 仓库已说明的能力 | 对本次需求的建议 |
|---|---|---|
| [plearaj/code-flow-skill](https://github.com/plearaj/code-flow-skill) | 沿入口追踪调用链；生成 Mermaid、Markdown、JSON 和可交互 HTML；提供 Java tracer 与源码位置 | **需要持续生成“方法入口 → 全链路文档”时优先考虑。** 仓库流程还包含补充源码注释；使用前应按你的要求限制为只读分析和图文输出 |
| [yunusemregul/callgraph](https://github.com/yunusemregul/callgraph) | IntelliJ IDEA 方法调用图；节点跳转定义，边跳转调用位置；支持展开调用关系和导出 HTML | **主要在 IDEA 中边看图边读 Java 时优先考虑。** 将光标放在 ask 上，右键 Generate Call Graph，或按 Alt+Shift+E；应查看 callees 方向以向下追踪 |
| [Trail of Bits diagramming-code](https://github.com/trailofbits/skills/blob/main/plugins/trailmark/skills/diagramming-code/SKILL.md) / [Trailmark](https://github.com/trailofbits/trailmark) | 从解析后的代码图生成 Mermaid 调用图、类关系图和依赖图；Trailmark 支持 Java | **需要可重复生成的静态代码关系图时考虑。** 需要 Trailmark 环境；本项目的工具回调关系仍应对照注册点核验 |

对这段代码，建议将自动生成的调用图用于定位方法，将本文件的流程图和时序图用于理解分支、预算及历史回填。静态方法边本身不能证明某次请求实际走过的工具路径；实际路径需要对应请求的 `trace` 或运行日志。
