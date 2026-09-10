# 02｜亲手看见 Agent Loop

[上一课](01-foundations.md) · [返回总入口](README.md) · [下一课](03-retrieval-and-reliability.md)

## 1. 实验要证明什么

今天只回答四个问题：决策如何转成工具调用？工具结果如何回到下一轮？失败能不能被看见？循环何时结束？

[AgentLoopDemo.java](AgentLoopDemo.java) 用预设函数模拟模型，根据工具结果分支，便于反复重现。它不理解自然语言、不读真实文件、不调用 LLM，也不验证真实模型的可靠性。目录路径是虚构字符串，搜索逻辑只是字符串匹配。

## 2. 准备与运行

安装 JDK 21 后，打开 PowerShell，进入存放本课 AgentLoopDemo.java 的文件夹。已有 Java 环境先检查版本，无需重装。

```powershell
java -version
java '-Dfile.encoding=UTF-8' AgentLoopDemo.java
java -ea '-Dfile.encoding=UTF-8' AgentLoopDemo.java --check
```

最后一条应输出：

```text
PASS: offline loop, results, denial, validation and step budget
```

这使用 Java 单文件源码运行方式，不需要 Maven，不会新增项目依赖。`java` 找不到时，检查 JDK 安装和 PATH；遇到 `getLast()` 等方法不存在，检查是否实际使用了 JDK 21。`--check` 必须带 `-ea`，防止断言被关闭而误报通过。

## 3. 读懂一次输出

```text
CALL Call[id=call-1, name=search_notes, query=Java]
RESULT Observation[callId=call-1, ok=true, text=study-notes/java-agent.md]
FINAL Read this source: study-notes/java-agent.md
STATUS DONE
```

`CALL` 是请求；`RESULT` 才是执行结果。相同的 `call-1` 把它们关联起来。`FINAL` 表示模拟决策函数选择了回答，`DONE` 只表示正常结束，**不代表答案经过事实验证**。

这里总共发生两次决策：第一次请求搜索，第二次读取观察并回答。一次 Agent 运行可以有多次模型调用；模型的一次响应也可能包含多个工具调用。本示例仅演示每次一个、串行执行的情况。

## 4. 按执行顺序看源码

| 方法 | 职责 | 为什么单独看它 |
|---|---|---|
| `main` | 读取练习关键词，启动一次运行 | 输入不等于任务已经完成 |
| `run` | 管理循环、记录、调用关联和停止 | 找到“程序控制”的部分 |
| `scriptedModel` | 模拟模型提出调用或最终回答 | 真实接入时替换的是决策来源 |
| `execute` | 工具白名单、参数检查和固定目录搜索 | 模型不能绕过工具约束 |
| `check` | 验证几个确定性边界 | 不用“输出看起来对”当测试 |

`run` 将只读的观察列表交给决策函数，状态都属于当前调用，没有全局可变会话历史。示例不共享会话，也不实现并发；真实服务需要按用户和会话隔离状态，不能简单把历史列表放进一个 Spring 单例字段。

`maxSteps` 限制决策次数，不是工具超时。真实 HTTP 调用仍要有单次超时、总运行时限、取消和 Token/金额预算。本例没有网络等待，不能拿它证明超时控制已完成。

## 5. 四个小实验

### A：正常命中

```powershell
java '-Dfile.encoding=UTF-8' AgentLoopDemo.java RAG
```

应出现 `study-notes/rag.md`。注意工具没真正打开这个文件，因此最终回答只能推荐入口，不能声称已经阅读其全部内容。

### B：资料不足

```powershell
java '-Dfile.encoding=UTF-8' AgentLoopDemo.java Kubernetes
```

应出现 `NO_MATCH`，然后说明缺少依据。查询成功但没匹配项，不等于工具执行错误，更不证明整个互联网没有资料。

### C：参数错误

```powershell
java '-Dfile.encoding=UTF-8' AgentLoopDemo.java ' '
```

应出现 `ok=false` 和 `INVALID_ARGUMENT`，最后说明工具失败。观察失败返回为什么也需要关联调用 ID。

### D：死循环与越权

运行 `--check` 会自动检查“模型一直要求搜索”时返回 `LIMIT`、请求 `delete_file` 被拒绝、重复调用 ID 和非法决策被阻止。打开 `check()`，找到对应输入，并手写你预期的结果后再执行。

记录表：

| 场景 | 预期 | 实际 | 哪层决定结果 |
|---|---|---|---|
| 正常搜索 | 返回资料入口 | 自己填写 | 工具 + 决策 |
| 无匹配 | 说明不足 | 自己填写 | 工具 + 决策 |
| 空参数 | 明确失败 | 自己填写 | 参数校验 |
| 无限搜索 | LIMIT | 自己填写 | 运行循环 |

## 6. Javaer 怎样进入真实框架

默认选 Spring AI；喜欢接口声明式开发可选 LangChain4j。只选一套完成练习，不必同时学两套。

| 你已理解的机制 | Spring AI 阅读入口 | LangChain4j 阅读入口 |
|---|---|---|
| 模型请求与消息 | ChatModel / ChatClient | ChatModel / AiServices |
| 工具定义和执行 | Tool Calling | Tools / ToolSpecification |
| 会话上下文 | Chat Memory | ChatMemory |
| 结果与错误 | 官方工具调用示例及日志 | 官方工具执行与异常说明 |

从 [Spring AI 官方示例](https://github.com/spring-projects/spring-ai-examples) 或 [LangChain4j 官方示例](https://github.com/langchain4j/langchain4j-examples) 选一个最小对话案例，保存所用 commit、JDK、框架和模型版本，再加一个只读工具。API 签名分别查 [Spring AI Tools](https://docs.spring.io/spring-ai/reference/api/tools.html) 与 [LangChain4j Tools](https://docs.langchain4j.dev/tutorials/tools/)。

2026 年视频也可能使用不同主版本。不要将旧视频的依赖版本与当前文档代码混搭；沿用同一个官方示例的完整依赖配置。联网模型可能计费，先自行设置预算和环境变量；不把 Key 写进源码或日志。

**教学观察题**：假设工具 `get_time(zone)` 声称支持时区，方法体却只调用 `LocalDateTime.now()`。解释这个实现为什么与描述不符。再设计一个合法时区和一个非法时区输入的验证；这是一道独立练习。

## 7. 真实模型实验的下一步规格

以后允许联网实验时，保持相同的只读任务，使用所选 Provider 的官方工具调用格式：传入工具定义，解析模型调用，执行后按 call ID 回填结果，再决定继续或停止。不要把本例 `Decision` 的字符串形式直接当某个供应商的 API JSON。

至少验证：真实模型能否选择正确工具、是否填写合法参数、看到 `NO_MATCH` 是否承认不足、工具报错是否有界重试。API 的 HTTP 成功和最终答案正确是两项不同的检查。

本课程不要求输出模型私有思维链；记录可观察的工具名、参数摘要、结果、耗时和停止原因即可。
