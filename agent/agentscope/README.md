# AgentScope Java 学习与落地路线

> 核验日期：2026-09-01；当前仓库基线：AgentScope Java `1.0.9`；主学习版本：AgentScope Java `2.0.1`。
> 最终目标：借鉴 Pi Agent 与 DeepSeek Harness 的学习方法，完成一个只读 Java 代码调查 Agent。

## 1. 结论先行

采用“20% 看 1.0，80% 学 2.0”的路线：

```text
现有 1.0.9 示例：只负责建立对照基线
        ↓
2.0 Core：Message / Model / Tool / ReActAgent / AgentEvent
        ↓
2.0 状态与控制：RuntimeContext / AgentState / Middleware / Permission / HITL
        ↓
2.0 Harness：Workspace / Filesystem / Session / Compaction / Plan Mode / Skill
        ↓
项目落地：只读 Java 代码调查 Agent
```

新项目直接使用 2.0；1.0 只用于理解本仓库旧代码和迁移差异。官方 FAQ 也建议新项目直接采用 2.0。当前稳定基线为 `2.0.1`，发布时间是 2026-08-05。

虽然官方 Quickstart 直接推荐 `HarnessAgent`，本路线会先学裸 `ReActAgent`，再叠加 Harness。这样可以先看清 Agent Loop，再理解工程能力是如何挂载上去的。

## 2. 版本边界

| 范围 | 当前或目标版本 | 用途 | 决策 |
| --- | --- | --- | --- |
| 根 `pom.xml` | `1.0.9` | 现有模块依赖管理 | 保持不动 |
| 当前 `agent/agentscope` | 1.0 API | 迁移标本 | 只运行、对照，不继续扩展 |
| 后续 2.0 实验模块 | `2.0.1` | 学习与新项目 | 独立锁定版本 |
| 官方源码 | tag `v2.0.1` | 源码事实基线 | 不按浮动 `main` 复制 API |
| Python / TypeScript AgentScope | 独立实现 | 非本路线范围 | 不混用教程和 API |

不要直接把根属性 `agentscope.version` 改成 `2.0.1`。2.0 的模型 Provider 已拆成独立 Maven 模块，消息、状态、事件、Hook 和 Session 等 API 也有不兼容变更；全局升级会同时影响现有 AgentScope 示例和 `product/pdf-export`。

以后进入编码阶段时，新建独立的 `agent/agentscope-v2-lab` 模块，并在模块内固定：

```text
io.agentscope:agentscope-harness:2.0.1
io.agentscope:agentscope-extensions-model-<实际厂商>:2.0.1
```

第一阶段只选择一个模型 Provider，不引入 RAG、MCP、数据库、Web UI 或额外模型 SDK。

## 3. 学习前的 Stop Gate：先完成密钥治理

仓库审计发现，旧 Key 不仅曾出现在当前示例中，也已进入 Git 历史和远端跟踪引用。删除当前源码不能使旧 Key 失效，也不能清除历史。

任何联网模型实验开始前，必须满足：

- [ ] 对仍有效的旧 Key 在各厂商控制台执行撤销。
- [ ] 当前阶段不创建新 Key；进入联网实验前，再创建低权限、低额度、专用于学习的 Key。
- [ ] 源码、注释、YAML、`.env`、README 和截图均不含明文 Key。
- [ ] 新 Key 通过环境变量、IDE 私有 Run Configuration 或密钥管理服务注入。
- [ ] 工作树密钥扫描为零。
- [ ] 明确记录“当前树已清理”和“Git 历史已清理”是两项不同证据。
- [ ] 如需重写 Git 历史，先单独评审远端、分支、Tag、协作者和强推影响。

当前 1.0 示例只读取 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`；图片生成脚本读取 `DASHSCOPE_API_KEY`。当前阶段这些变量均保持未设置，不进行联网模型调用。

Gate 0：旧 Key 已失效、工作树无明文 Key、根 POM 仍为 `1.0.9`，才开始调用模型。

## 4. 从 Pi / DSH 迁移到 AgentScope 2.0

| 学习维度 | Pi Agent | DeepSeek Harness | AgentScope Java 2.0 | 学习重点 |
| --- | --- | --- | --- | --- |
| Model | `pi-ai` | Provider / LLM Service | `Model`、`ModelRegistry`、Provider 扩展 | Provider 已拆包 |
| Message | 统一消息格式 | Session Event 派生视图 | `Msg` + 类型化 `ContentBlock` | 消息与事件分开 |
| Event | Agent / Tool 生命周期事件 | Durable Event + 实时事件 | `streamEvents()` + `AgentEvent` | 用于进度、工具和 HITL |
| Loop | Agent Run / Turn | Turn / Step / Tool Pipeline | `ReActAgent` reasoning-acting loop | 一次 call 可多次调模型和工具 |
| Tool | Tool + Extension Hook | Tool Service + 审批流水线 | `Toolkit`、`AgentTool`、`@Tool` | 契约、错误、并发和权限 |
| 扩展 | Extension / Hook | Cordis Plugin / Service | Middleware + Toolkit + SkillRepository | 不做机械一一映射 |
| Session | AgentSession + JSONL | append-only Session Log | `RuntimeContext` + `AgentStateStore` | 身份、状态、日志分层 |
| Harness | `pi-coding-agent` | 整个平台 | `HarnessAgent` | 是 ReActAgent 的工程包装层 |
| Safety | Trust 不等于沙箱 | Approval 不等于 Sandbox | Permission + Filesystem + Sandbox | 提示词和审批不能代替隔离 |

AgentScope 2.0 中最容易混淆的是四个概念：

```text
RuntimeContext
  本次 call 的 userId、sessionId、requestId 和业务上下文；自由属性不持久化。

AgentState
  当前会话的消息、摘要、权限、Plan 和工具状态。

AgentStateStore
  AgentState 的跨调用、跨进程持久化后端，按 (userId, sessionId) 寻址。

Workspace
  AGENTS.md、MEMORY.md、skills、计划、session log 等长期文件资产。
```

2.0 的 Agent 实例是无状态执行引擎，但业务状态并没有消失，而是由 `RuntimeContext` 定位独立的 `AgentState`。生产调用必须同时设置 `userId` 和 `sessionId`。

## 5. 四周总路线

默认每周 5 天、每天 1.5～2 小时。是否进入下一阶段只看 Gate，不按日历强行推进。

| 周次 | 核心问题 | 主要产出 | Gate |
| --- | --- | --- | --- |
| 第 1 周 | 一次 Agent 调用如何完成 | Core 最小实验与事件轨迹 | 能解释完整 ReAct 因果链 |
| 第 2 周 | 状态、并发、权限如何控制 | 会话隔离、Middleware、HITL 实验 | 无串话，拒绝操作无副作用 |
| 第 3 周 | Harness 多做了什么 | Workspace、持久化、压缩、Plan 实验 | 能解释状态与安全边界 |
| 第 4 周 | 如何收敛成窄场景项目 | 只读 Java 代码调查 Agent | 功能、安全、隔离、失败路径全部验收 |

## 6. 第 1 周：Core 主链

### 学习内容

1. `UserMessage`、`AssistantMessage`、`Msg`、`ContentBlock`。
2. Model Provider、formatter 和凭据的边界。
3. `Toolkit`、`@Tool`、`@ToolParam`、`ToolBase`。
4. `ReActAgent` 的 reasoning / acting 循环。
5. `call()`、`streamEvents()` 和最终消息的关系。
6. 工具参数错误、工具异常、最大迭代、取消和正常停止。

### 递进实验

实验 1：无 Tool 的最小调用

- 只注册模型，不注册 Tool。
- 记录 Agent 开始、模型调用、文本增量和 Agent 结束事件。
- 能说明为什么没有进入 acting。

实验 2：确定性只读 Tool

- 实现 `count_text` 或 `describe_fixture`，不使用时间、网络和随机数。
- 覆盖正常参数、缺失参数、错误类型。
- 验证错误会成为模型可理解的 Tool Result，模型可以自纠。

实验 3：完整 Tool Loop

- 设计一个必须调用 Tool 才能回答的问题。
- 记录 `模型 → Tool Call → Tool Result → 模型 → 最终回答`。
- 区分一次 `call()`、一次模型请求和一次工具调用。

实验 4：修复现有时间工具

- 当前 `SimpleTools.getTime(zone)` 接收 `zone`，实际使用主机默认时区。
- 将它作为第一个 Tool Contract 练习：使用 `ZoneId`，给出合法示例，错误时返回明确的参数错误。
- 这一步放在 2.0 实验模块中完成，不继续扩展旧 1.0 示例。

### Gate 1

- [ ] 能画出 `message → model → tool → result → model → final`。
- [ ] 能解释 `Msg` 与 `AgentEvent` 的区别。
- [ ] 能解释 Tool 的只读性、幂等性和并发安全。
- [ ] 参数错误能回灌给模型。
- [ ] 正常停止、最大迭代、取消和异常具有不同终态。
- [ ] 所有实验固定 `2.0.1`，无明文 Key。

## 7. 第 2 周：状态、Middleware、Permission 与 HITL

### 学习内容

1. `RuntimeContext`：一次调用的身份与业务上下文。
2. `AgentState`：当前会话的可变运行状态。
3. `AgentStateStore`：自动 load / save 与重启恢复。
4. Middleware 的 agent、reasoning、acting、model-call、system-prompt 切面。
5. Permission 的允许、询问和拒绝决策。
6. `RequireUserConfirmEvent` 与 HITL 恢复。
7. 单例 Agent 服务多用户、多会话时的隔离和并发。

新代码直接学习 Middleware；不要继续投入已进入迁移路径的 1.x Hook。旧 `SessionManager` 和 `StatePersistence` 也应转向 `AgentStateStore`。

### 递进实验

实验 5：四格会话隔离

| 调用 | userId | sessionId | 预期 |
| --- | --- | --- | --- |
| A | alice | s1 | 记住 A 的信息 |
| B | alice | s2 | 不读取 s1 的会话历史 |
| C | bob | s1 | 不读取 alice 的状态 |
| D | alice | s1 | 能续接 A |

实验 6：进程重启恢复

- 使用单机 `JsonFileAgentStateStore`。
- 第一轮结束后关闭进程，再以相同 `(userId, sessionId)` 调用。
- 相同标识应恢复；改变任一标识都不能串话。

实验 7：权限拒绝无副作用

- 只读 Tool 自动允许。
- 模拟写操作进入 ASK。
- 用户拒绝后验证事件闭合，且目标文件 SHA-256 不变。

实验 8：审计 Middleware

- 只记录 requestId、模型耗时、Tool 名称、状态和错误类型。
- 不记录完整 Prompt、密钥或敏感 Tool 参数。
- 正常、失败和取消路径都要闭合。

### Gate 2

- [ ] 四格隔离实验全部通过。
- [ ] 能解释为什么 `RuntimeContext` 不等于 Session。
- [ ] 能解释为什么 Agent 无状态不等于业务无状态。
- [ ] ASK / DENY 后没有副作用。
- [ ] Tool Call、Tool Result 与 HITL 事件可关联。
- [ ] Middleware 没有绕过 Toolkit、Permission 或状态链路。

## 8. 第 3 周：Harness 工程能力

`HarnessAgent` 不改写 ReAct 算法，而是在其上叠加 Workspace、状态持久化、长期记忆、上下文压缩、Filesystem、Sandbox、Plan Mode、Skill、子 Agent 和 Channel。

这一周先学 Workspace、State、Compaction、Permission、Plan Mode 和 Filesystem；Skill、子 Agent 只理解入口，不做复杂编排。

### 递进实验

实验 9：Workspace 动态人格

- 使用一次性 Workspace，添加最小 `AGENTS.md`。
- 完成一轮后修改行为规则，再发起下一轮。
- 验证 system prompt 每轮重建，规则修改无需重启即可生效。

实验 10：三层状态观察

```text
调用内：RuntimeContext + AgentState
跨调用：AgentStateStore + session JSONL
跨会话：MEMORY.md + memory/YYYY-MM-DD.md
```

逐项记录数据放在哪里、由谁写入、下一轮和下一会话是否可见。

实验 11：Compaction

- 仅在实验中调低触发阈值。
- 产生多轮消息和较大的 Tool Result。
- 验证摘要保留目标、约束、关键结果和未完成项。
- Tool Call / Tool Result 不能被拆坏，原始 session log 仍应完整。

实验 12：Plan Mode 与文件系统边界

- Plan Mode 中读取 fixture，并尝试写文件和执行命令。
- 使用 `LocalFsMode.ROOTED`，测试项目内路径、`../` 和工作区外绝对路径。
- 通用写操作和 execute 应被拒；路径穿越和额外根目录访问应失败。

### Gate 3

- [ ] 能解释 `ReActAgent` 与 `HarnessAgent` 的职责边界。
- [ ] 能解释 AgentState、session log、MEMORY.md 的不同生命周期。
- [ ] 能解释 Plan Mode、Permission、Workspace、Filesystem、Sandbox 各解决哪层风险。
- [ ] 知道本机 Filesystem 提供的是宿主环境能力，权限规则不是操作系统沙箱。
- [ ] 知道 `tools.json` 在 build 时读取，修改后需重新 build Agent。
- [ ] 沙箱或远端模式下的文件访问走 `WorkspaceManager`，不直接用 `java.nio.Files`。
- [ ] 目标 fixture 运行前后 SHA-256 清单一致。

## 9. 源码阅读顺序

不要从仓库根目录全量逐文件读，也不要第一次就顺序啃完整 `ReActAgent.java`。固定 tag `v2.0.1`，沿一条真实调用链阅读：

1. Quickstart 和可运行示例。
2. Message / AgentEvent。
3. Tool、Toolkit 与 Permission。
4. `ReActAgent`：Builder、`call()`、`streamEvents()`、reasoning、acting、Tool Result 回填、停止路径。
5. `RuntimeContext`、`AgentState`、`AgentStateStore`。
6. `MiddlewareBase` 和执行顺序。
7. `HarnessAgent` 与 Builder 支持类。
8. `WorkspaceManager`、Filesystem Tool 和路径校验。
9. 最后才读 Compaction、Plan Mode、Skill、Subagent。

每读一层只记录六件事：

```text
入口
稳定契约
默认实现
状态写入位置
扩展点
失败与安全边界
```

## 10. 第 4 周：只读 Java 代码调查 Agent

### 10.1 项目目标

输入：

```text
projectPath + question + userId + sessionId
```

输出：

```markdown
## 结论

## 证据
- src/.../Foo.java:42
- src/.../Bar.java:87

## 未确认项

## 工具轨迹
```

只回答能被真实源码支持的内容；找不到证据时输出“未确认”，不能补猜测。

### 10.2 最小架构

```text
CLI
  ↓
HarnessAgent
  ↓
RuntimeContext(userId, sessionId)
  ↓
PermissionMode.EXPLORE
  ↓
list_files / glob_files / grep_files / read_file
  ↓
LocalFilesystemSpec(ROOTED, projectWritable=false, inheritEnv=false)
  ↓
人工构造的 Java fixture
```

第一版使用 CLI，不先做 Spring Boot。Workspace、状态目录和目标项目分开；Harness 可以维护自己的状态与会话文件，但目标 Java 项目保持只读。

### 10.3 工具白名单

Workspace 的 `tools.json` 只暴露读取工具：

```json
{
  "allow": [
    "read_file",
    "grep_files",
    "glob_files",
    "list_files"
  ],
  "deny": [
    "write_file",
    "edit_file",
    "execute"
  ],
  "mcpServers": {}
}
```

安全边界分三层：

1. 未在白名单中的 Tool 不暴露给模型。
2. Permission 再拒绝写入和命令执行。
3. `ROOTED` 路径策略拒绝 `..` 和额外根目录。

如果以后要执行不可信命令，再引入 Docker 或远程 Sandbox；不要把 Permission 当作 Sandbox。

### 10.4 Fixture 设计

第一版只用小型、人工构造的 Java 项目：

```text
Controller
  → ApplicationService
  → DomainService
  → Mapper
  → XML / SQL
```

至少包含：

- 同名方法，防止仅靠字符串命中即可猜答案。
- 一条正常调用链。
- 一条未使用的旧实现。
- 一个状态枚举映射。
- 一个 Mapper XML 查询条件。
- 一个故意缺失证据的问题。

不连接真实数据库，不读取生产仓库，不发送含密钥的文件给模型。

### 10.5 验收清单

功能：

- [ ] 能回答 Controller 到 Mapper 的真实调用链。
- [ ] 每个核心结论至少给出两个正确的 `path:line` 证据。
- [ ] 证据行与结论直接相关，不只是命中文件名。
- [ ] 找不到证据时明确写“未确认”。

安全：

- [ ] 模型可见 Tool 只有四个只读工具。
- [ ] `write_file`、`edit_file`、`execute` 不可见或被拒。
- [ ] `../` 和工作区外绝对路径被拒。
- [ ] 未配置 MCP 和网络工具。
- [ ] 目标项目运行前后 SHA-256 清单一致。
- [ ] Key 不在目标项目、Workspace、日志或回答中。

状态与失败路径：

- [ ] 相同 `(userId, sessionId)` 可以续问。
- [ ] 同用户不同 Session、不同用户相同 Session 均不串话。
- [ ] 同一 Session 的并发调用不会损坏状态。
- [ ] Tool 参数错误可自纠。
- [ ] 文件不存在、编码异常、大文件都有边界结果。
- [ ] 模型超时、用户取消、最大迭代都有明确终态。
- [ ] Agent Start / End 或错误事件闭合。

Gate 4：以上清单全部通过，才进入真实项目试点。

## 11. 暂不做什么

第一版明确不做：

- Spring Boot Web 接口。
- RAG 和向量数据库。
- MCP。
- 多 Agent。
- 自动修改代码。
- Shell 编译和测试。
- 生产数据库访问。
- AG-UI / Web UI。
- Redis / MySQL 分布式 StateStore。
- 自动生成或晋升 Skill。

只有只读调查 Agent 的验收暴露真实需求后，再逐项增加。不能因为框架提供了能力，就默认项目必须使用它。

## 12. 实验记录模板

```text
实验编号：
AgentScope 版本 / Git tag：
JDK / Maven：
模型 Provider：
输入：
预期：
实际：
事件轨迹：
Tool Call / Result：
状态存储变化：
目标项目哈希变化：
失败与未知项：
结论：
```

只把真实运行结果写成“已验证”；源码推断、官方文档说明和目标环境运行结果分开表述。按仓库规则，实验测试文件只保留本地，不提交 Git。

## 13. 官方资料阅读顺序

1. [2.0 Release Notes](https://java.agentscope.io/v2/zh/docs/others/release-notes.html)
2. [Quickstart](https://java.agentscope.io/v2/zh/docs/quickstart.html)
3. [V1 迁移指南](https://java.agentscope.io/v2/zh/docs/change-log.html)
4. [消息与事件](https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html)
5. [Tool](https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html)
6. [Context 与 AgentState](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html)
7. [Permission System](https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html)
8. [Harness 架构](https://java.agentscope.io/v2/zh/docs/harness/architecture.html)
9. [Workspace](https://java.agentscope.io/v2/zh/docs/harness/workspace.html)
10. [Filesystem](https://java.agentscope.io/v2/zh/docs/harness/filesystem.html)
11. [Plan Mode](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html)
12. [Going to Production](https://java.agentscope.io/v2/zh/docs/others/going-to-production.html)
13. [AgentScope Java GitHub](https://github.com/agentscope-ai/agentscope-java/tree/v2.0.1)

本仓库的 [Pi Agent 学习笔记](../pi-agent/README.md) 和 [DeepSeek Harness 学习笔记](../deepseek-harness/README.md) 只作为概念对照，不重复学习其完整使用教程。

## 14. 当前 1.0 示例的最小使用方式

环境要求：Java 21、Maven 3.8+。当前代码只用于验证 1.0 基线：

```powershell
mvn -q -pl agent/agentscope -am -DskipTests compile
mvn -q -pl agent/agentscope -Dexec.mainClass=cn.tyron.llm.QuickStart exec:java
```

当前阶段不运行联网示例。以后进入联网实验时，再在 IDE 私有 Run Configuration 中设置 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`；不要把值写入源码、README 或可持久化的终端历史。
