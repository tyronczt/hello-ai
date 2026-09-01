# DeepSeek Harness 学习笔记

> 整理日期：2026-09-01<br>
> 最新源码研究基线：`dsh-v0.1.2-alpha.3` / `dd6322d604e00eec1ba5e0c8541159906a21094a`<br>
> npm 默认渠道兼容基线：`dsh-v0.1.1-rc.2` / `b150a551b8d465e31e418e1b2eaf5e79bbb7d28e`<br>
> 目标：参考 Pi Agent 已建立的 Agent Loop、Tool、Session 和扩展知识，系统理解 DeepSeek Harness，并能完成一个最小插件和一个可复现 Agent 实验。

## 1. 先说结论

DeepSeek Harness（简称 DSH）是一个开源、可组合的 Agent Harness。它负责把模型、提示词、工具、会话、权限、事件和界面组装成能持续工作的 Agent；它不是 DeepSeek 模型本身，也不只支持 DeepSeek 模型。

官方用一句话概括它的设计：`Agent = Model + Harness`。DSH 最值得学习的不是又一个聊天界面，而是三件事：

1. 如何用 Cordis 把 Agent 各能力做成可替换插件。
2. 如何用追加写事件日志连接模型上下文、回放、分支和审计。
3. 如何把 Tool Call 放进包含权限、审批、并发和结果固化的完整流水线。

当前项目仍是 **Developer Preview**。学习架构没有问题，但不要把未审计的 DSH、第三方插件或 Agent 生成的命令直接放进生产环境运行。

### 1.1 两条版本线不要混用

截至 `2026-09-01`，Git、GitHub Release 与 npm 默认渠道并不指向同一版本：

| 口径 | 当前结果 | 用途 |
| --- | --- | --- |
| Git `master` | `0.1.2-alpha.3` / `dd6322d` | 阅读最新源码 |
| 最新 Git tag / GitHub Release | `dsh-v0.1.2-alpha.3`，Pre-release | 研究最新功能 |
| npm `alpha` | `0.1.2-alpha.3` | 运行最新预览版 |
| npm `latest` / `next` | `0.1.1-rc.2` | 复现默认安装和多数已有教程 |

因此本文采用双基线：

- 看最新架构：固定 `0.1.2-alpha.3` 或提交 `dd6322d`。
- 跟现有社区教程：固定 `0.1.1-rc.2`，再核对教程自己的 commit。
- 不把浮动的 `master`、`latest` 或 `alpha` 当作可复现版本。

版本证据可从 [官方 Releases](https://github.com/deepseek-ai/deepseek-harness/releases)、[npm dist-tags](https://registry.npmjs.org/-/package/@deepseek-ai%2fdsh/dist-tags) 和 [CLI package.json](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/apps/cli/package.json) 交叉核对。

### 1.2 `0.1.2-alpha` 更新了什么

`0.1.2-alpha.1` 到 `alpha.3` 的 Release 重点不只是版本号：

| 版本 | 与学习最相关的变化 |
| --- | --- |
| `alpha.1` | 子 Agent 可在授权范围内选择 Provider、模型和推理力度；Claude Code / Codex 子 Agent 支持模型配置；ACP 补齐 Session 控制、模型、MCP、权限和取消；Python SDK 增加 Windows x64 runtime；DeepSeek 适配器增加默认关闭的 Session 日志增量上传 |
| `alpha.2` | Web 端可按 Session / Global 查看插件并切换、搜索 Agent Preset；增加连接失败与重试状态、Schedule 展示、Token 与耗时明细；`SessionEvent.ignorable` 在一次移除后恢复，说明事件 API 仍在快速调整 |
| `alpha.3` | 优化长会话分页导航、内存和代码高亮；运行中排队的图片及可持续子 Agent 后续图片能可靠投递；`read_image` 支持无扩展名附件；移除可选 SQLite Session 持久化后端 |

迁移时最需要注意最后一项：旧 SQLite 内容不会被自动删除，但新版本不再提供该后端；如果曾使用它，应先用旧版本导出，再切换版本。完整变更见 [`alpha.1`](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.1.2-alpha.1)、[`alpha.2`](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.1.2-alpha.2) 和 [`alpha.3`](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.1.2-alpha.3) Release。

当前源码的能力面已经包括 Goal、Plan、Schedule、Subagent、Background Jobs、Workflow / Ralph、Hooks、运行时 Extensions、SDK 和 ACP。它们都是可组合能力，不代表每个 Profile 或 Preset 默认全部启用；先从 [Packages 地图](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/packages/README.md) 找契约与实现，再检查最终配置树。

## 2. 参考 Pi Agent，哪些知识可以迁移

DSH 是独立项目，不是 Pi Agent 的分支。当前仓库既有独立的 DeepSeek 原生适配器，也包含 `llm-pi-ai` 适配器，可以复用 `@earendil-works/pi-ai` 的模型接入能力；但 DSH 自己实现 Agent Loop、事件、会话持久化和 Cordis 插件体系。

| Pi Agent 中的概念 | DSH 中最接近的概念 | 迁移时要注意 |
| --- | --- | --- |
| `pi-ai` | LLM Service / `llm-pi-ai` Provider | 可以复用模型适配层，不能据此认为两者 Loop 相同 |
| `pi-agent-core` Agent Loop | `agent-loop` Service | 都是 `模型 -> 工具 -> 结果 -> 模型`，DSH 额外强调事件和能力接缝 |
| `AgentSession` + JSONL Session | Session Service + 追加写事件日志 | DSH 从日志派生模型消息、投影、回放和恢复 |
| Extension | Cordis Plugin | DSH 插件可注册 Service、事件和可撤销 Effect |
| Skill / Prompt / Package | Plugin、Agent Preset、Bundle 等组合 | 不是一一对应，不要把 Pi 名词硬套到 DSH |
| CLI / TUI / SDK / RPC | Web、headless、SDK、`sdk-minimal`、ACP | DSH 当前学习入口更偏 Web 和组合式 Profile |
| Hook | typed event / middleware / tool pipeline | 先判断事件模式，再决定监听、改写还是中断 |

### 2.1 可以直接复用的学习问题

- 一次 Turn、一次 Step、一次 Tool Call 分别从哪里开始和结束？
- Tool 参数如何校验，失败结果如何写回模型上下文？
- 无副作用读取何时并行，写操作为什么需要稳定顺序？
- 会话恢复时，什么是持久事实，什么只是运行时瞬态事件？
- 权限、审批和沙箱分别解决哪一层风险？
- 模型、工具、Session 或 UI 能否被替换，替换边界在哪里？

### 2.2 需要重新学习的部分

- Cordis 的 Context、Service、Plugin、Effect 和 typed event。
- Application Profile、Bundle、Patch 与 Agent Preset 的区别。
- 追加写 Session Event Log 如何派生模型消息和轨迹。
- Service Definition、Provider、Consumer 组成的 capability seam。
- DSH 的 Web、headless、SDK、ACP 等不同组装方式。

## 3. 核心架构

```text
Application Profile
  web / headless / sdk / sdk-minimal / acp
                    |
                    v
Bundle + cordis.patch.yml + Home Patch + CLI Patch
  选择插件、服务实现和配置
                    |
                    v
Cordis Context
  Plugin / Service / typed event / reversible Effect
                    |
        +-----------+------------+-------------+
        |           |            |             |
        v           v            v             v
    Sessions   System Prompt    Tools       Agents
        |                        |             |
        +------------------------+-------------+
                                 |
                                 v
                           Agent Loop
                     LLM stream <-> Tool pipeline
                                 |
                                 v
                     append-only session event log
                 deriveMessages / projection / replay
```

不要把这张图理解成固定分层框架。DSH 的核心主张是 **Everything is a plugin**：模型、工具、会话、Loop 和界面都可以由插件提供，Profile 只是在某个运行场景下选择并组合这些能力。上述结构以 [`dd6322d` 架构快照](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/architecture.zh.md) 为准。

## 4. 先分清四组容易混淆的名词

### 4.1 Application Profile

Profile 是一套具名应用组装方案，决定启动时加载哪些 Bundle、插件和配置。当前源码文档列出的 Profile 包括：

- `web`：完整 Web 应用，也是 `dsh web` 的目标 Profile。
- `headless`：无 Web UI 的应用组合。
- `sdk`：供宿主应用嵌入的 SDK 组合。
- `sdk-minimal`：更小的 SDK 组合。
- `acp`：Agent Client Protocol 方向的组合。

它回答的是“这个 DSH 应用由哪些能力组成”，不是“当前会话采用什么 Agent 行为”。

### 4.2 Bundle

Bundle 是插件与配置的分发、安装单位。一个 Profile 可以引入多个 Bundle，再由 Patch 覆盖某些配置。

### 4.3 Patch

当前架构文档给出的覆盖顺序是：

```text
Profile 自带 Bundle
  -> Profile 的 cordis.patch.yml
  -> Harness Home Patch
  -> 命令行 --patch
```

配置行按 `id` 匹配时是整行替换，不是任意对象的深度合并。调配置前必须查看最终装配结果，不能只读某一份 YAML 就断言运行配置。

### 4.4 Agent Preset / Mode

Preset 决定新 Session 的 Agent 行为、提示词和能力选择。[当前源码随附](https://github.com/deepseek-ai/deepseek-harness/tree/dd6322d604e00eec1ba5e0c8541159906a21094a/packages/preset/agent-presets/presets)：

| 展示模式 | 适合场景 | 学习重点 |
| --- | --- | --- |
| Standard（`standard`） | 通用 Agent 任务 | 默认能力如何组合 |
| PTC（`ptc`） | 通过代码调用工具的任务 | 代码生成、嵌套 Tool Call 与门禁重入 |
| Minimal（`minimal`） | 最小能力实验 | 去掉高级能力后 Loop 还剩什么 |
| Creator（`cordis`） | 创建和调试 DSH 能力 | Cordis、插件和配置组装 |

这里存在版本命名漂移：官网落地页仍写 **Code Mode**，而 `0.1.2-alpha.3` 当前随附 preset 已是 `standard / ptc / minimal / cordis`，用户显示名对应“标准 / PTC / 极简 / 创造”。复制旧教程中的 `code` preset 路径前必须先核对当前源码。Preset 通常在创建 Session 时确定，修改默认 Preset 不应被理解为自动改写已有 Session；实际可选项仍以当前 Profile 和配置为准。

## 5. Cordis：DSH 的插件底座

学习 Cordis 时先抓住五个概念：

1. **Plugin**：向 Context 安装能力的最小模块。
2. **Context**：插件协作的运行容器，也是 Service 和事件的访问入口。
3. **Service / inject**：Provider 注册能力，Consumer 声明依赖，避免靠全局变量互相查找。
4. **typed event**：插件通过有类型的事件协作，而不是直接耦合彼此实现。
5. **Effect**：安装时产生副作用，同时登记 disposer；插件卸载或 Context 销毁时可以撤销。

最小插件可以只是导出一个 `apply(ctx)` 函数。需要提供 Service 或管理复杂生命周期时，再使用类；不要一开始就抽象插件框架。

### 5.1 事件模式不是同一种广播

Cordis 支持多种事件语义，使用前要确认调用方需要什么：

| 模式 | 直觉 | 常见用途 |
| --- | --- | --- |
| `emit` | 依次通知监听器 | 普通生命周期通知 |
| `parallel` | 并行通知 | 互不依赖的观察者 |
| `serial` | 串行收集或执行 | 顺序敏感的处理 |
| `bail` | 首个有效结果即可结束 | 查找 Provider、拦截或决策 |
| `waterfall` | 前一结果传给后一处理器 | 逐层变换数据 |

Middleware 风格的 waterfall 还涉及 `next()`。如果把它误当普通广播，很容易出现后续处理器不执行或顺序错误。

### 5.2 Capability Seam

一个可替换能力通常可以拆成：

```text
Service Definition：稳定契约
        |
        +---- Provider：一种具体实现
        |
        +---- Consumer：只依赖契约的使用方
```

读源码时先找 Service Definition，再分别找 Provider 和 Consumer，比从目录第一行开始顺序阅读更高效。

## 6. Agent Loop 与事件链

一次典型 Turn 可以压缩为下面这条链：

```text
turn/start
  -> 认领用户输入
  -> 组装系统提示词、历史消息和工具
  -> agent/pre-step
  -> step/start
  -> user/message
  -> agent/request
  -> llm/stream
  -> assistant chunk / assistant message
  -> tool/call（如果模型请求工具）
  -> Tool Pipeline
  -> tool/result
  -> step/end
  -> 若仍有工具结果需要模型处理，则进入下一 Step
  -> agent/turn-stopping
  -> turn/end
```

理解时要区分：

- **Turn**：围绕一次被认领的用户输入完成的外层工作单元。
- **Step**：一次模型请求及其产生的工具处理阶段；一个 Turn 可以有多个 Step。
- **Tool Call**：模型请求的一次能力调用；同一 Step 可能有多个。
- **停止**：可能来自模型不再请求工具、用户取消、预算或步数限制、错误、插件决策，不能只看最终有没有文本。

事件名称和先后顺序是学习当前实现的入口，不是承诺永久不变的公共 API。Developer Preview 阶段复制事件名之前必须回查当前类型定义和测试。

## 7. Tool 执行流水线

DSH 不应被理解成“收到 Tool Call 后直接调用函数”。当前官方文档描述的主链是：

```text
tool/call
  -> tools/pre-execute
  -> 单调安全门禁（只能收紧，不能由后续阶段放宽）
  -> execute wrapper
  -> 工具主体 / 文件系统门禁 / 进程执行
  -> tools/post-execute
  -> finalize
  -> immutable tool/result
```

重点问题：

- 参数 schema 在哪里校验，错误怎样转成模型可理解的结果？
- `allow / ask / deny` 由谁决定，用户拒绝后如何结束当前调用？
- 多个 Tool Call 可以并发到什么程度？
- 即使实际执行并发，`pre-execute`、`post-execute` 与结果固化如何保持可预测顺序？
- Hook 能否修改参数或结果，修改之后由谁再次校验？
- Tool Result 一旦写入 Session，后续插件是否还能改变历史事实？

安全策略应满足“单调收紧”：后续处理器可以把 `allow` 变成 `ask` 或 `deny`，不应把前面已经拒绝的操作重新放行。

## 8. Session、事件日志与 Trajectory

DSH 把追加写 Session Event Log 放在架构中心。可以用三层理解：

```text
持久层：append-only session events
   |
   +-> 模型视图：deriveMessages，派生下一次请求所需消息
   +-> UI 视图：projection，派生页面状态
   +-> 调试视图：trajectory / replay，重放发生过的过程
   +-> 分支与恢复：从已有事实继续，而不是改写旧记录
```

关键原则：

- 对模型可见的长期信息，应能追溯到 Session Log；只发运行时事件不等于进入模型上下文。
- Durable Session Event 与实时 Agent/Capability Event 不是一回事。前者是持久事实，后者可能只用于协作和观察。
- 追加写不代表日志中的每个事件都原样送给模型；`deriveMessages` 会生成模型需要的视图。
- Compaction 应保留目标、约束、工具结果中的关键事实和未完成事项，同时不破坏 Tool Call / Tool Result 配对。
- 回放用于解释“为什么走到这里”，不等于重新执行外部副作用。

建议第一次运行时不要只看最终回答，同时观察 Session 事件、工具审批和轨迹，这才是学习 Harness 的主材料。

## 9. 安全边界

官方安全说明明确指出：项目尚未完成安全审计，也未宣称达到生产安全。DSH 可以执行模型生成的代码和命令、加载插件，并可能访问网络、进程、凭据和文件系统。

必须记住：

- **Approval 不是 Sandbox**：审批减少误操作，不能隔离恶意代码或插件。
- **Workspace 限制不等于 OS 隔离**：进程继承的主机权限仍可能触达工作区外资源。
- **Plugin 是受信任同进程代码**：安装第三方插件前先审查源码、依赖、安装脚本和网络行为。
- **Minimal 不代表最安全**：当前 `sdk-minimal` 组合固定为 `danger-full-access`，它的“最小”指应用组合，不是权限最小化。
- **提示词不是权限系统**：不能用“不要读取密钥”的文字代替凭据隔离。
- **回放不是回滚**：Session 有轨迹不代表外部文件、数据库和网络副作用自动恢复。

学习环境建议：

1. 使用一次性目录、容器或虚拟机，不直接挂载真实项目和 SSH 目录。
2. 使用专门的低权限 API Key，限制额度，不把 Key 写入仓库。
3. 默认拒绝写文件、运行命令和网络访问，按实验逐项放开。
4. 对真实项目先做备份或使用临时分支，检查每次 diff。
5. 不在生产数据库、云账号或公司内网权限下试运行未知插件。

详见 [`dd6322d` 官方 SAFETY](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/SAFETY.zh.md)。

## 10. 最小运行与实验

### 10.1 固定版本启动

当前源码仓库声明 Node.js `^22.19.0 || >=24.0.0`，并使用 `pnpm@11.7.0`；从源码构建时按这条基线准备环境。当前 npm 发布包自身没有声明 `engines`，这不等于旧 Node 已获支持，运行时仍应记录实际 Node 版本和结果。

```bash
# 最新源码研究线：精确固定 alpha.3
npx -y @deepseek-ai/dsh@0.1.2-alpha.3 web --no-open

# npm 默认渠道与已有社区教程兼容线
npx -y @deepseek-ai/dsh@0.1.1-rc.2 web --no-open
```

默认 Web 地址是 `http://127.0.0.1:3080`。第一次实验在 Settings 中配置模型，在新建 Session 时选择一个一次性工作区。模型密钥放在本机配置或环境中，不写进学习笔记和 Git。

官方未固定版本的快速启动命令是 `npx @deepseek-ai/dsh web`，它适合体验，不适合作为实验报告中的复现命令。

### 10.2 五个递进实验

#### 实验 A：只读观察 Loop

- 准备只有 2～3 个文本文件的一次性目录。
- 只允许读取，让 Agent 总结文件关系。
- 在 Session 轨迹中标出 `turn/start`、模型请求、Tool Call、Tool Result 和 `turn/end`。
- 验收：能解释 Turn、Step 与 Tool Call 的数量为什么不同。

#### 实验 B：审批与拒绝

- 请求 Agent 新建一个文件，但在审批阶段拒绝。
- 观察 Tool Result、Session 状态和最终回复。
- 验收：磁盘没有变化，轨迹能说明拒绝发生在哪一层。

#### 实验 C：最小 Tool Plugin

- 按官方教程实现一个无副作用工具，例如统计输入文本长度。
- 给出明确 schema，并分别测试合法参数、缺失参数和错误类型。
- 验收：工具可卸载；卸载后不残留监听器或服务；错误能安全返回模型。

#### 实验 D：Session 恢复与分支

- 完成一轮多工具任务，关闭后恢复 Session。
- 从某个历史节点建立分支，提出相反要求。
- 验收：旧历史没有被改写；两条分支能说明各自上下文来源。

#### 实验 E：对照 Pi Agent

- 在 Pi 和 DSH 中实现同一个只读 Tool。
- 对比模型适配、Loop 事件、Session 文件、扩展注册和卸载方式。
- 验收：写出三项可迁移设计和三项 DSH 独有机制，不只比较 UI。

## 11. 源码阅读顺序

不要从 monorepo 根目录逐文件扫描。按一次真实请求的链路阅读：

1. [README](https://github.com/deepseek-ai/deepseek-harness) 与 [产品页](https://www.deepseek.com/harness/)：确认定位、启动入口和模式。
2. [CLI 入口](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/apps/cli/src/bin.ts)：从启动命令进入 Profile 装配，不从 Web 页面反推后端结构。
3. [架构文档（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/architecture.zh.md)：建立 Profile、Bundle、Cordis 和核心 Service 地图。
4. [Agent 生命周期（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/agent-lifecycle.zh.md)：顺着一个 Turn 读事件链。
5. [Tool 执行流水线（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/tool-execution-pipeline.zh.md)：理解审批、并发和结果固化。
6. [Agent Loop 主实现](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/packages/core/agent-loop/src/agent.ts)：把文档事件映射到代码。
7. [Cordis Primer](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/cordis-primer.zh.md) 与 [Cordis Tutorial](https://deepseek-harness.github.io/deepseek-harness/develop/cordis-tutorial/)：学习插件生命周期和事件模式。
8. [配置目录](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/docs/config-catalog.zh.md) 与 [Packages 地图](https://github.com/deepseek-ai/deepseek-harness/blob/dd6322d604e00eec1ba5e0c8541159906a21094a/packages/README.md)：查最终装配和 Provider。
9. [`llm-pi-ai` 适配器](https://github.com/deepseek-ai/deepseek-harness/tree/dd6322d604e00eec1ba5e0c8541159906a21094a/packages/llm/llm-pi-ai)：对照 Pi 只读模型适配边界，不从这里推断整个 DSH 架构。

每读一层只记录四项：稳定契约、默认实现、替换入口、持久化影响。不要大段抄源码。

## 12. 推荐资源

### 12.1 官方事实源

1. [DeepSeek Harness 官方仓库](https://github.com/deepseek-ai/deepseek-harness)
   - 源码、Release、示例和最终事实来源。
2. [官方产品页](https://www.deepseek.com/harness/)
   - 用图文理解 `Agent = Model + Harness`、事件轨迹和四种模式。
3. [中文 Quickstart](https://deepseek-harness.github.io/deepseek-harness/guide/quickstart)
   - 启动、模型设置、工作区和权限审批。
4. [Architecture（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.zh.md)
   - 当前最重要的整体架构事实源。
5. [Agent Lifecycle（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/agent-lifecycle.zh.md)
   - 从 Session 输入到 Tool 和 Turn 结束的事件序列。
6. [Tool Execution Pipeline（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/tool-execution-pipeline.zh.md)
   - 权限、审批、并发、Hook 和结果固化。
7. [第一个插件](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/user/develop/basic/index.zh.md) 与 [Tool 教程](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/user/develop/basic/tool.zh.md)
   - 完成最小插件实验的首选材料。
8. [Extension Cookbook（中文）](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/cookbook/extension-cookbook.zh.md)
   - 按能力类型查插件写法，不建议第一次就通读。
9. [Python SDK](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/user/guide/python-sdk.md)
   - 适合从外部应用调用 DSH。当前文档要求 Python 3.10+，并说明最小持久 PTY Agent 的支持平台；Windows 使用前先核对最新支持状态。
10. [Cordis 论文](https://arxiv.org/abs/2608.25512)
    - 适合完成插件实战后研究设计背景，不是入门前置材料。

### 12.2 中文系统资料

1. [deepseek-harness-deep-dive](https://github.com/hoco-scy/deepseek-harness-deep-dive)
   - 以源码引用为主的系统化深挖资料，并标注研究 commit；适合第二阶段逐章对照源码。
2. [deepseek-harness-tutorial](https://github.com/ht426/deepseek-harness-tutorial)
   - 中文章节式教程，适合先建立概念地图，再回官方文档确认 API。
3. [Bin-hy/dsh](https://github.com/Bin-hy/dsh)
   - 社区学习与实验资料；使用前检查它当前锁定的 DSH 版本。
4. [deepseek-harness-handbook](https://github.com/sandbaseai/deepseek-harness-handbook)
   - 社区实践手册，可补充操作视角，不能替代官方源码事实。

社区教程的架构图可以帮助理解，但事件名、包路径、配置键和安装命令必须回到其标注 commit 或当前官方源码核对。

### 12.3 B 站视频

| 资源 | 适合解决的问题 |
| --- | --- |
| [架构与 Cordis 可视化讲解](https://www.bilibili.com/video/BV1Pugw63EQ5/) | 快速建立“Everything is a plugin”地图 |
| [DeepSeek Harness 源码学习系列](https://www.bilibili.com/video/BV14a846cETY/) | 跟随执行链阅读源码 |
| [Plugin Workflow 与 Preset 实战](https://www.bilibili.com/video/BV1C48n6gEhu/) | 区分插件工作流和会话预设 |
| [从零开发 DSH 插件](https://www.bilibili.com/video/BV1Dig36CERE/) | 完成第一个插件并查看配套项目 |
| [用 ComfyUI 插件理解真实集成](https://www.bilibili.com/video/BV16J8v63E5f/) | 观察外部系统接入、配置和失败面 |

这些视频发布于项目早期迭代阶段，适合建立直觉，不适合直接复制版本、命令和 API。

### 12.4 YouTube 精选

1. [Deepseek Harness: Everything is a plugin](https://www.youtube.com/watch?v=xe-aHJLC5UU)
   - 短时间理解安装、Trajectory、配置和插件化定位。
2. [DeepSeek Harness Just Changed AI Forever](https://www.youtube.com/watch?v=DTu4yvmc0Fc)
   - 从 Cordis、Creator 和事件轨迹理解产品主张；标题偏传播，技术结论仍需回官方文档。

视频数量不多，先看官方文档再看演示，收益高于连续刷测评。

### 12.5 可运行示例与社区项目

1. [官方 JSON-RPC Agent 示例](https://github.com/deepseek-ai/deepseek-harness/tree/master/examples/jsonrpc-agent)
   - 包含基础文件、命令、子 Agent、Todo、JSONL 与上下文压缩，适合研究外部接入。
2. [dsh-mcp-apps](https://github.com/sugarforever/dsh-mcp-apps)
   - 与插件视频配套的 MCP 应用实验。
3. [dsh-code-review](https://github.com/CooStack/dsh-code-review)
   - 用具体 Code Review 场景观察插件或工作流如何落地。
4. [dsh-review](https://github.com/Tinzlu/dsh-review)
   - 另一种 Review 实现，适合对照边界和复杂度，不建议两个项目同时照抄。
5. [dsh-feishu-bridge](https://github.com/wz-heng/dsh-feishu-bridge)
   - 学习把 DSH 接到外部消息渠道时的会话和权限边界。
6. [GitHub `dsh-plugin` Topic](https://github.com/topics/dsh-plugin)
   - 发现新插件的入口。安装前检查维护状态、版本锁定、依赖脚本和权限。

### 12.6 Gitee 检索结论

截至 `2026-09-01`，本轮没有检索到 DeepSeek 官方 Gitee 仓库，也没有找到维护状态和版本来源都足以替代 GitHub 官方仓库的镜像。本笔记不为凑数量收录来源不明的镜像。

如果 GitHub 访问不稳定，优先使用官方文档站、npm 精确版本或可信代理读取指定 commit，不从未知镜像安装 DSH 和插件。

## 13. 推荐学习路线

### 阶段一：用 Pi 知识建立 DSH 地图

- [ ] 读本文第 1～4 节，能区分 Model、Harness、Profile 和 Preset。
- [ ] 读官方 README、Quickstart 与 SAFETY。
- [ ] 画出 `Profile -> Cordis -> Core Services -> Agent Loop -> Session Log`。
- [ ] 写下 Pi 与 DSH 的五组对应关系，并标记哪两组不是一一对应。

### 阶段二：跑通并观察一条请求

- [ ] 固定精确版本启动 Web Profile。
- [ ] 只在一次性只读目录完成实验 A。
- [ ] 从轨迹中标出 Turn、Step、Tool Call 和 Tool Result。
- [ ] 完成拒绝写操作的实验 B，确认磁盘无变化。

### 阶段三：沿执行链读源码

- [ ] 阅读 Architecture、Agent Lifecycle 和 Tool Pipeline。
- [ ] 从 `agent.ts` 追踪一次 `user -> llm -> tool -> result -> llm`。
- [ ] 找到一个 Service Definition、Provider 和 Consumer。
- [ ] 解释 Durable Session Event 与实时 Agent Event 的区别。
- [ ] 对照 `llm-pi-ai`，说明复用了什么、没有复用什么。

### 阶段四：写最小插件

- [ ] 完成无副作用 Tool Plugin，不增加第三方依赖。
- [ ] 覆盖正常参数、缺失参数、错误类型和主动拒绝。
- [ ] 验证插件卸载时 Effect 能恢复。
- [ ] 查看最终 Profile 装配，确认插件来自哪个 Bundle / Patch。
- [ ] 固定 DSH 版本和插件 commit，记录复现命令。

### 阶段五：做一个窄场景 Agent

- [ ] 选择一个具体任务，例如只读代码审查，不做“万能 Agent 平台”。
- [ ] 明确模型、工具、权限、审批、超时、取消和审计边界。
- [ ] 选择 Web、headless、SDK 或 ACP 中最小的交付入口。
- [ ] 在容器或虚拟机验证正常完成、工具失败、模型失败、用户取消和 Session 恢复。
- [ ] 和 Pi 实现做一次对照复盘，记录选择 DSH 的真实理由。

## 14. 学完后的自测题

1. 为什么 DSH 使用了 `pi-ai` 仍然不是 Pi Agent 的一个 UI？
2. `web` Profile 与 `PTC` Preset 分别决定什么？为什么旧页面还可能写 `Code Mode`？
3. Plugin、Bundle 和 Patch 在安装与运行时各负责什么？
4. 哪些事件必须进入 Session Log，哪些只需作为实时通知？
5. 一个 Turn 为什么可能包含多个 Step？
6. Tool 审批为什么不能替代容器隔离？
7. 为什么配置按行替换会让“只看一份 YAML”得出错误结论？
8. 如何证明插件卸载后没有残留监听器或 Service？
9. 恢复 Session 与重放外部副作用有什么区别？
10. 面对 `0.1.2-alpha.3` 源码和 `0.1.1-rc.2` npm 默认版，应如何选择和记录？

如果能结合一条真实 Session 轨迹回答这些问题，而不是只背术语，说明已经掌握了 DSH 主线。

## 15. 后续更新规则

每次更新本笔记先运行：

```bash
git ls-remote --symref https://github.com/deepseek-ai/deepseek-harness.git HEAD
npm view @deepseek-ai/dsh dist-tags --json
```

然后只更新四类变化：

1. 最新源码 commit、GitHub Release 与 npm dist-tag 的差异。
2. Profile、Preset、核心 Service 和配置覆盖顺序。
3. Agent Lifecycle、Tool Pipeline 和 Session 事件契约。
4. 真正补充新视角或可运行案例的资料。

更新时保留旧版本 commit，删除已经失效且无法映射到固定版本的命令。文档与社区教程冲突时，以同一 commit 下的官方源码、类型定义和测试为准。
