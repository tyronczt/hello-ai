# Pi Agent 学习笔记

> 整理日期：2026-09-01
> 目标：理解 Pi 的 Agent Runtime、Coding Agent Harness，并能基于 SDK/Extension 构建自己的 Agent。

## 1. 一句话认识 Pi

Pi 是一个极简、可扩展的终端 Coding Agent Harness。它既可以直接作为命令行编码助手使用，也可以把底层包作为 SDK 嵌入自己的应用。

它的核心思路不是预置所有高级能力，而是提供少量稳定原语，再通过 Extension、Skill、Prompt Template 和 Pi Package 组合出具体工作流。

## 2. 核心架构

```text
pi-coding-agent
  CLI / SDK / AgentSession / Session / Compaction
  内置工具 / Extension / Skill / Prompt / TUI 组装
                     |
                     v
pi-agent-core
  Agent 状态 / Agent Loop / Tool Call / 事件流 / 队列
                     |
                     v
pi-ai
  多 Provider 模型抽象 / 流式响应 / 消息与 Tool Call 格式

pi-tui
  独立的终端 UI 组件，被 coding-agent 使用
```

当前官方仓库还包含 `pi-telemetry` 等辅助包；学习主线先抓住 `pi-ai -> pi-agent-core -> pi-coding-agent` 三层即可。

### 2.1 pi-ai

- 屏蔽 OpenAI、Anthropic、Google 等 Provider 的接口差异。
- 统一模型、消息、流式事件、Tool Call、Token 和用量信息。
- 可以单独使用，不要求引入 Agent Loop 或 TUI。

### 2.2 pi-agent-core

- 保存 Agent 当前状态：模型、系统提示词、消息、工具和运行状态。
- 驱动 Agent Loop。
- 执行工具，并发出消息、回合和工具生命周期事件。
- 处理用户中途追加消息、停止和错误状态。

### 2.3 pi-coding-agent

- 把模型、Agent、工具、Session、Extension 和界面组装成完整产品。
- 提供交互模式、Print/JSON、RPC 和 SDK 等使用方式。
- 负责项目上下文、会话持久化、分支、压缩和资源加载。

### 2.4 pi-tui

- 提供终端组件和渲染能力。
- 与 Agent Runtime 解耦，不是理解 Agent Loop 的前置知识。

## 3. Agent Loop

```text
用户消息
  -> AgentSession 组装上下文
  -> Agent 调用模型
  -> 模型输出文本或 Tool Call
  -> 参数校验与调用前 Hook
  -> 串行/并行执行工具
  -> 调用后 Hook 处理结果
  -> Tool Result 写回消息上下文
  -> 再次调用模型
  -> 模型不再调用工具，或发生终止/错误/取消
  -> 本轮结束
```

### 3.1 必须理解的问题

- 为什么“模型不再请求工具”通常代表 Loop 可以结束？
- 一次 Turn 和一次完整 Agent Run 有什么区别？
- Tool Call 参数在哪里校验，错误如何反馈给模型自纠错？
- 同一批 Tool Call 何时适合并行，何时必须串行？
- 用户运行中追加的 steering/follow-up 消息在什么时点进入队列？
- `beforeToolCall`、`afterToolCall` 和 Extension 事件分别适合处理什么逻辑？
- 模型错误、工具错误、用户取消和正常停止如何区分？

### 3.2 工具并发原则

- 无副作用的读取操作通常可以并行。
- 写文件、执行命令或依赖前一步结果的操作应保持串行。
- 并发完成顺序不等于 Tool Call 原始顺序，结果回填必须保持协议要求的稳定顺序。
- 安全检查和参数校验不能因为并行执行而省略。

## 4. Session 与上下文

### 4.1 两层 Session

学习时需要区分：

1. `AgentSession`：运行时编排对象，负责与 Agent 交互，并协调模型、工具、扩展、配置和事件。
2. 持久化会话：保存在本地的 JSONL 会话树，用于恢复、分支和追踪历史。

### 4.2 Compaction

上下文接近模型窗口上限时，Pi 会总结较旧消息并保留近期内容，随后使用“摘要 + 保留消息”继续运行。

重点关注：

- 压缩触发阈值和预留输出 Token。
- 切分点不能破坏 Tool Call 与 Tool Result 的配对。
- 工具输出通常是上下文膨胀的主要来源，应先截断无价值输出。
- 摘要需要保留目标、约束、已修改文件、未完成事项和关键错误。
- 分支摘要与普通上下文压缩的目的不同。

## 5. Extension、Skill 与 Prompt

| 机制 | 主要作用 | 典型场景 |
| --- | --- | --- |
| Extension | 增加或拦截运行时能力 | 自定义工具、权限门禁、命令、事件、Provider、TUI |
| Skill | 按需提供任务知识和步骤 | Code Review、发布流程、领域操作规范 |
| Prompt Template | 复用用户侧提示词 | 固定检查流程、重复任务入口 |
| Pi Package | 打包和分发资源 | 共享 Extension、Skill、Prompt、Theme |

Extension 本质上是受信任代码，拥有启动 Pi 进程用户的系统权限。安装第三方 Extension 或 Package 前必须审查源码；需要强隔离时使用容器或沙箱，而不是只依赖提示词约束。

## 6. B 站合集分析

### AI_Julie：合集·pi agent

- 地址：[合集·pi agent](https://space.bilibili.com/524275099/lists/8835704?type=season)
- 规模：16 集，总时长约 4 小时 52 分钟。
- 定位：源码执行链路导读，不是纯使用入门课。
- 前置知识：TypeScript、异步编程、流式响应和 Tool Calling。

内容结构：

1. `00`：快速认识和使用 Pi Agent。
2. `01`：Loop、Extension、TUI 总体架构。
3. `02`：官方文档阅读记录，作者标注不建议观看，可跳过。
4. `03.1~03.5`：调用栈、消息进入 Loop、消息队列、RunLoop、单次模型调用。
5. `03.6~03.11`：工具串并行、兜底、事件、Session、Compaction、Human in the Loop。
6. `04`：Extension 概览，内容较短。
7. `05`：最终架构串讲。

推荐路线：

- 快速理解：`00 -> 01 -> 05`，约 1 小时 38 分钟。
- 源码主线：`00 -> 01 -> 03.1~03.8 -> 03.10 -> 03.11 -> 05`。
- 可以跳过或选听：`02`、`03.9`；`04` 只作为 Extension 导览。

优点：按真实执行路径拆源码，覆盖并发工具、压缩、人工介入和异常兜底。
不足：没有公开字幕和课程总结；Extension、SDK、RPC、安全和测试没有充分展开。

## 7. 推荐资源

### 7.1 官方事实源

1. [Pi 官方文档](https://pi.dev/docs/latest)
   - 安装、Provider、Session、Compaction、Extension、Skill、SDK、RPC、安全与容器化。
2. [earendil-works/pi](https://github.com/earendil-works/pi)
   - 当前源码和最终事实来源。
3. [官方 SDK 与 Extension 示例](https://github.com/earendil-works/pi/tree/main/packages/coding-agent/examples)
   - 学完概念后应直接运行和修改这里的示例。

### 7.2 中文系统教程

1. [Pi Agent 双轨教程](https://dg-ai-notes.pages.dev/)
   - 10 章源码拆解、7 章 DataAgent 实战、TypeScript/Python 对照、Agent Loop 实验场。
2. [dg-ai-notes GitHub](https://github.com/buchidonggua/dg-ai-notes)
   - Markdown、配套代码、PDF 和 `dg-piagent` Skill。
3. [Pi 官方文档中文同步](https://pi-agent.org/docs)
   - 便于中文检索；不是官方站，API 和配置仍需回查英文官方文档。

### 7.3 B 站视频

1. [Pi Agent 源码系统课：从真实运行到自己组装 Agent](https://www.bilibili.com/video/BV1zagQ6BEry)
   - 54 分钟总览，基于 Pi `0.81.1`，覆盖 Provider、Loop、Tool、Session、Extension、SDK/RPC 和安全边界。
2. [使用 pi-agent 的三种姿势，它凭什么成为 OpenClaw 的底层框架](https://www.bilibili.com/video/BV1CuNG6pERs)
   - 约 12 分钟，用极简 Coding Agent 理解“模型 + 工具 + 循环”。
3. [哦对了，我将 pi 源码写成了一本书](https://www.bilibili.com/video/BV12WK666EhM)
   - `dg-ai-notes` 导读，偏生产工程视角。
4. [我把 Pi Agent 源码拆开了：从 Agent Loop 到扩展机制](https://www.bilibili.com/video/BV1AHNu6mEDJ)
   - 约 4 分钟快速预览，不替代系统课程。

### 7.4 架构图与源码索引

1. [pi-mono-docs](https://github.com/mudrii/pi-mono-docs)
   - 按包和主题组织的源码索引，适合查入口；内容锁定在特定版本。
2. [pi-mono 中文设计文档](https://yeluo45.github.io/pi-mono-design/docs/02-pi-agent-core)
   - Agent 状态机、事件流、工具并发、Queue、Hook、Session 与 Compaction 图解。
3. [How Pi Works](https://alejandro-ao.com/pi-architecture/)
   - 从 Agent Core 与 Interactive Harness 两层理解整体架构。

### 7.5 YouTube 精选

先按主题选看，不建议从社区索引里的全部视频顺序刷起。

| 资源 | 时长 | 适合解决的问题 |
| --- | ---: | --- |
| [pi - a radically minimal, opinionated multi-model coding agent](https://www.youtube.com/watch?v=4p2uQ4FQtis) | 约 12 分钟 | 从作者演示理解 Pi 为什么保持极简 |
| [PI Architecture EXPLAINED](https://www.youtube.com/watch?v=gTeujlv8qK0) | 约 39 分钟 | 系统理解 Agent Loop、Tools、TUI 和整体分层 |
| [Pi Building Pi](https://www.youtube.com/watch?v=DPgJjRdQWrg) | 约 91 分钟 | 听作者解释设计取舍和自举式开发过程 |
| [Hacking pi using pi](https://www.youtube.com/watch?v=dWlhu0aWCb8) | 约 133 分钟 | 观察真实长流程开发，不只看整理后的架构结论 |
| [Embedding The Pi Coding Agent In Your Product](https://www.youtube.com/watch?v=vAIDdLKB6-w) | 约 21 分钟 | 学习把 Pi SDK 嵌入产品，而不是只使用 CLI |
| [How Pi Mono Actually Works in Your App](https://www.youtube.com/watch?v=lWbEq1dEqKg) | 约 18 分钟 | 理解应用侧如何组合共享 Agent Stack |
| [Agentic Security For Pi Agent and Claude Code](https://www.youtube.com/watch?v=yBcmIoA-vGs) | 约 31 分钟 | 思考 Bash、权限边界和工具攻击面 |
| [Coding Agent from Scratch: Terminal UI with pi-tui](https://www.youtube.com/watch?v=_K4c7cL_tzw) | 约 17 分钟 | 单独学习 TUI 组件与终端交互 |
| [Deploying Pi Coding Agents in Docker Sandboxes](https://www.youtube.com/watch?v=P7AZ-iDbIoc) | 约 4 分钟 | 快速了解容器隔离的落地方式 |

更多视频可从 [PeopleOfPi Videos](https://peopleofpi.dev/videos/) 按 Interview、Tutorial、Demo 等分类继续筛选。该索引当前收录百余个视频，适合发现资料，不应当作 API 事实来源。

### 7.6 实战项目与扩展源码

1. [官方 Subagent Extension 示例](https://github.com/earendil-works/pi/tree/main/packages/coding-agent/examples/extensions/subagent)
   - 先读这个最小实现，再看社区多 Agent 框架，避免一开始就陷入复杂编排。
2. [OpenUI Pi Agent Harness](https://github.com/thesysdev/openui/tree/main/examples/harnesses/pi-agent-harness)
   - 在 Next.js 后端嵌入 Pi，并将 Agent 输出流式渲染为 Web UI；适合学习 SDK、服务端流和安全边界。
3. [pi-mobile](https://github.com/lhr0909/pi-mobile)
   - TypeScript SDK Host 加 Expo 客户端，适合学习 WebSocket 协议和移动端接入。
4. [pi-agent-extensions](https://github.com/ang-XWBWZ/pi-agent-extensions)
   - 包含并行 Agent、危险操作门禁、模型路由、观测和 Windows 适配；适合读 Extension 工程实践，不建议未经审查整包安装。
5. [Emacs frontend for Pi](https://github.com/dnouri/pi-coding-agent)
   - 通过 RPC 将 Pi 接入编辑器，适合理解 CLI 之外的前端集成边界。
6. [Rust port of Pi](https://github.com/nktkt/pi)
   - 用另一种语言对照核心语义；它不是官方实现，不能用来确认 TypeScript API。

### 7.7 包市场与社区入口

1. [Pi Package Registry](https://pi.dev/packages)
   - 官方包索引，可继续查找 Extension、Skill、Theme 和 Prompt；安装前仍需审查源码和权限。
2. [PeopleOfPi Repositories](https://peopleofpi.dev/repos/)
   - 社区项目导航，适合按 SDK、Extension、UI 和工具类别发现实例。
3. [r/PiCodingAgent](https://www.reddit.com/r/PiCodingAgent/)
   - 适合发现使用问题和新项目；经验帖需要回到官方文档或源码验证。

### 7.8 Gitee 检索结论

截至 `2026-09-01`，没有检索到维护活跃、版本明确、可替代当前官方仓库的 Pi 专项 Gitee 镜像或系统教程。搜索结果主要是泛 Agent 项目、GitHub 镜像或与 Pi 无关的最小 Agent 示例，因此本笔记不为凑数量收录。

如果访问 GitHub 不稳定，优先使用 `pi.dev` 文档、npm 包元数据或可信代理读取官方仓库，不要从来源不明的 Gitee 镜像安装 Agent 或 Extension。

## 8. 推荐学习路线

### 阶段一：建立地图

- [ ] 看“使用 pi-agent 的三种姿势”。
- [ ] 看 54 分钟源码系统课。
- [ ] 阅读官方 Quickstart、Using Pi 和 Security。
- [ ] 选看 YouTube 的作者短讲与架构解析，不连续刷完整视频库。
- [ ] 能画出 `pi-ai -> pi-agent-core -> pi-coding-agent` 依赖图。

### 阶段二：读懂运行链路

- [ ] 阅读 `dg-ai-notes` 的三层架构、Agent Loop、模型、工具、消息和事件章节。
- [ ] 对照官方源码追踪一次 `prompt -> model -> tool -> result -> model`。
- [ ] 弄清 Turn、Run、Session、Queue 和 Event 的边界。
- [ ] 记录每个关键入口的文件路径和职责，不抄整段源码。

### 阶段三：动手修改

- [ ] 运行官方最小 SDK 示例。
- [ ] 新增一个只读工具，并验证参数错误能回灌给模型。
- [ ] 写一个危险命令拦截 Extension。
- [ ] 对照 OpenUI Harness 或 pi-mobile，完成一次 SDK/RPC 外部接入。
- [ ] 分别观察串行和并行工具的事件顺序。
- [ ] 创建长会话，验证 Compaction、恢复和分支。

### 阶段四：做一个可交付 Agent

- [ ] 选择具体垂直场景，不做通用框架。
- [ ] 定义系统提示词、工具边界、权限和审计记录。
- [ ] 使用 SDK 或 RPC 接入应用。
- [ ] 对外部调用设置超时、取消、重试和幂等边界。
- [ ] 使用容器或沙箱隔离不可信代码执行。
- [ ] 补充端到端验证：正常完成、工具失败、模型失败、用户取消和上下文压缩。

## 9. Pi Agent vs DeepSeek Harness：先学哪个

### 9.1 结论

默认顺序：**先学 Pi Agent，再学 DeepSeek Harness**。

不需要把 Pi 的全部功能学完再切换，更合理的投入比例是：

```text
Pi Agent：约 30%，建立 Agent Harness 基础
DeepSeek Harness：约 70%，学习复杂平台架构与二开
```

如果只能选一个：

- 想掌握 Agent Loop、Tool、Session，并尝试自研 Java Agent Runtime：选 Pi。
- 想研究 Cordis、事件溯源、插件平台、多 Agent、Workflow 或 ACP：选 DeepSeek Harness。
- 还没有明确方向：先选 Pi，单位时间的学习收益更高。

完整的 DSH 架构、版本和实验资料见 [DeepSeek Harness 学习笔记](../deepseek-harness/README.md)。

### 9.2 核心比较

| 维度 | Pi Agent | DeepSeek Harness |
| --- | --- | --- |
| 官方定位 | 极简终端 Coding Agent Harness | `Everything is a Plugin` 的 Agent 平台 |
| 入门难度 | 较低，主线清晰 | 较高，需要先理解 Cordis 和应用装配 |
| 核心结构 | `pi-ai -> pi-agent-core -> pi-coding-agent` | Profile、Bundle、Patch、Cordis、Service、Effect、Preset |
| Agent Loop | 简洁，容易追踪完整因果链 | 事件丰富，权限和工具流水线更完整 |
| Session | JSONL 会话树、分支、Compaction | Append-only Event Log、Projection、Replay |
| 扩展方式 | ExtensionAPI 注册 Tool、Hook、命令和 UI | Cordis Plugin 可替换模型、Session、Loop 和策略 |
| 应用接入 | TypeScript SDK、RPC、JSON Event Stream | Web、headless、SDK、Python SDK、ACP |
| 当前版本状态 | npm `latest` 为 `0.84.4`，仍需注意 `0.x` 变化 | Developer Preview；最新源码为 `0.1.2-alpha.3`，npm `latest` 仍为 `0.1.1-rc.2` |
| 最适合学习 | Agent 的最小工作原理 | Agent 平台的完整工程架构 |

Pi 更适合作为第一个源码样本，因为模型适配、循环、工具和 Session 的边界容易看清。DSH 的架构深度更高，但如果还不理解一次模型请求为什么进入下一 Step，直接阅读插件树很容易先陷入配置和装配细节。

Pi 的知识不会浪费：DSH 是独立实现，但官方提供 `llm-pi-ai` Provider，可以复用 `pi-ai` 的模型适配能力；Agent Loop、Tool Pipeline、Session 和 Cordis 仍由 DSH 自己实现。

### 9.3 推荐学习顺序

#### 第一步：TypeScript 最小补课，2～3 小时

只补齐阅读源码需要的内容：

- `import / export`、接口、联合类型和泛型。
- `async / await`、Promise、AsyncIterable。
- Schema 校验、事件回调和 `AbortSignal`。

#### 第二步：Pi 主链，5～7 小时

1. 阅读本文第 1～5 节，不先研究 TUI。
2. 追踪一次 `prompt -> model -> tool -> result -> model`。
3. 理解 JSONL Session、分支、Compaction 和恢复。
4. 运行一个 SDK 示例，新增一个无副作用的只读 Tool。

满足以下条件即可切换 DSH：

- 能解释 Turn、Agent Run、Tool Call 和 Tool Result。
- 能画出一次完整 Agent Loop。
- 知道 Session 与 Compaction 分别解决什么问题。
- 能写一个最小 Tool 或 Extension，并处理错误参数。

#### 第三步：DeepSeek Harness 主链，12～18 小时

1. 用 Spring 类比 Cordis，但不强行一一对应：
   - Context 类似 `ApplicationContext`。
   - Service Definition 类似 Java 接口。
   - Provider 类似 Bean 实现。
   - inject 类似依赖注入。
   - Effect 类似带销毁回调的生命周期资源。
2. 区分 Profile、Bundle、Patch 和 Agent Preset。
3. 阅读 Agent Lifecycle、Tool Pipeline 和 Session Event Log。
4. 写一个无副作用 Tool Plugin。
5. 验证参数错误、审批拒绝、插件卸载和 Session 恢复。

Java 应用首次接入建议使用独立进程加 RPC、JSONL 或 ACP 边界，不要一开始把 Node.js 运行时嵌入 Spring Boot 进程。

### 9.4 什么情况下反过来先学 DSH

满足以下任一条件，可以 DSH 优先：

- 当前任务就是开发或维护 DSH 插件。
- 已经掌握 Agent Loop、Tool Calling、Session 和上下文压缩。
- 近期需要使用 DSH Web、headless、ACP、Python SDK 或多 Agent 能力。
- 团队已经选定 DSH，并能接受 alpha 阶段 API、配置和包结构变化。
- 研究目标就是 Cordis、动态插件生命周期或事件溯源架构。

即使 DSH 优先，也建议先花 60～90 分钟阅读本文第 1～4 节，再使用 DSH 笔记中的 Pi 概念映射进入源码。

### 9.5 安全边界

- Pi 没有用于限制文件系统、进程、网络和凭据访问的内置权限系统，默认继承启动用户权限。
- DSH 有审批、权限预设和沙箱插件，但官方仍明确表示尚未完成安全审计，也不保证完整隔离。
- 两者用于真实项目时都应考虑独立进程、容器或虚拟机；凭据、审批和审计由可信宿主掌握。

因此，“Pi 更容易学习”不代表“Pi 默认更安全”，“DSH 有权限界面”也不代表“DSH 可以直接执行不可信代码”。

## 10. 版本注意事项

- 当前官方仓库：`earendil-works/pi`。
- 当前包命名空间：`@earendil-works/*`。
- 截至 `2026-09-01`，`@earendil-works/pi-coding-agent` 的 npm `latest` 为 `0.84.4`。
- `badlogic/pi-mono`、`@mariozechner/*` 属于旧名称；旧链接可能仍会跳转，但代码不能直接混用。
- 第三方资料常固定在 `v0.74`、`v0.81`、`v0.83` 等版本，学习架构可以，复制 API 前必须核对当前源码。
- 视频标题里的 `pi-mono` 可能沿用历史项目名，先确认发布日期和 package namespace，再执行安装命令。
- 文档与源码冲突时，以当前官方源码、类型定义和测试为准。
- 与 DSH 对照时同时记录 GitHub Release 和 npm dist-tag；当前 DSH 最新源码与 npm 默认安装并非同一版本。

## 11. 后续更新规则

更新本笔记时只做三件事：

1. 记录当前官方版本或 commit。
2. 更新已经变化的入口、类型和配置，不重写仍然成立的架构结论。
3. 新资源只有在补充新视角或可运行实例时才加入，避免重复收藏同类教程。
