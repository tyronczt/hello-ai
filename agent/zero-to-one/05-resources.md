# 05｜2026 Agent 学习资源库：Javaer 独立路线

[学习入口](README.md) · [28 次学习计划](04-study-plan.md)

> 核验截止：2026-09-09。优先选择 2026 年实际发布资料与当前官方代码。视频推荐是基于页面、作者和目录的初筛，未完整观看或取得字幕；仓库主要核对 README 和入口，未批量运行代码。

## 先分清“新”的含义

- **2026 发布**：视频页面或文章正文有明确 2026 日期；不代表全部内容或 API 都是今年新增。
- **现行资料**：本次读取官方文档或 GitHub 当前入口。不能仅因今年访问过，就宣称仓库是 2026 新建或每章已更新。
- **补充候选**：经典资料或日期未逐项确认。独立列出，不混入 2026 新课。

下面保留较广的索引，但主修只选“概念教材 + 一个 Java 框架 + 一套评测练习”。不按播放量或标题里的“最全、精通、就业”判断质量。

## B 站：优先选看的 2026 视频

| 编号 | 资料 / 发布日期 | 作者与核验 | 适合阶段 / 选看内容 |
|---|---|---|---|
| B01 | [小白 AI Agent 学习指南](https://www.bilibili.com/video/BV1fj426wEHD/) · 2026-08-29 | AI_Julie；页面核验 | 第一天的路线预览，09:04；不当完整开发课 |
| B02 | [Spring AI 2.0：代码助手与工具调用课程](https://www.bilibili.com/video/BV1cBgG6wEpD) · 2026-08-12 | 图灵学院诸葛；搜索抓取到页面日期和 19 节目录，直访返回 412 | Spring 路线视频辅助：先 2～7 节的环境、对话、流式、记忆、压缩、工具；源码读写和 GitHub MCP 留到后面 |
| B03 | [小白系列：LangChain4j 从入门到精通](https://www.bilibili.com/video/BV1DDMt6HEA7/) · 2026-07-07 | 动力节点；浏览器核对日期、作者和 70 节目录 | Java 替代主线：2～6、8～13、28～33、40～51；介绍标称 1.14+，跟练锁定其示例版本 |
| B04 | [Java AI Agent、Spring AI Alibaba 与 Skill](https://www.bilibili.com/video/BV1PMEQ6eEhX/) · 2026-06-10 | 页面抓取可见日期、92 节目录；课程推广型内容，未核实代码可直接下载 | 完成基础之后，选 Agent/Workflow 区分、拦截、消息压缩；跳过求职宣传与重复章节 |
| B05 | [AgentScope 2.x：Java 意图路由与多 MCP 实战](https://www.bilibili.com/video/BV1LHgC65Era/) · 2026-07-22 | 页面摘要核对日期和技术栈；未核实全套源码 | 进阶比较路由、会话状态和工具挂载；描述包含权限 BYPASS，学习时须自行保持执行权限限制 |
| B06 | [AgentScope Java 2.0 核心组件篇](https://www.bilibili.com/video/BV185ED66EB1/) · 2026-06-10 | 都叫我大帅哥；浏览器核验作者、日期与 25 节目录，页面标注原创 | Java 进阶：先 1～6 的单 Agent，再 7～8 的会话/中断、12～18 的权限/工具异常；MCP/Skill 后置 |

**B03 推荐理由**：公开目录能逐步定位基础 API、声明式服务、记忆隔离、工具异常与并发，适合按问题选课。先完成单模型、内存会话与只读工具；MySQL / Redis / MQ 和业务登记项目后置。它与 Spring 路线二选一，不必刷两遍基础。

**B02 使用方式**：以官方 Spring AI 示例为代码依据，视频帮助理解步骤。目录里的外部仓库读写不是入门必做项；先把同一机制换成返回固定学习资料的只读工具。

部分视频的课件需要私信或站外领取，不能标注为“已确认公开源码”。本学习包提供官方 GitHub 示例作为替代，不要求付费或加入社群。

## GitHub：当前可查的教材、Java 框架与官方示例

时间标签统一为“2026-09-09 查阅的现行入口”，不冒称每个仓库创立于 2026。主修跟随选中版本；学习前记录 tag/commit，并在 Releases 或文件历史核对更新。

| 编号 | 仓库 | 语言 / 阶段 | 具体用法与前置 |
|---|---|---|---|
| G01 | [Hello-Agents](https://github.com/datawhalechina/hello-agents) | 中文教材，示例以 Python 为主；入门 | 学概念，不要求先把 Python 环境全部搭完 |
| G02 | [Generative AI for Beginners Java](https://github.com/microsoft/Generative-AI-for-beginners-java) | Java；基础 | 学消息、提示词、工具与 RAG；Azure/Codespaces 等部署支线按需选 |
| G03 | [AI Agents for Beginners](https://github.com/microsoft/ai-agents-for-beginners) | 通用原理 / Python；补充 | 用来对照工具、记忆与设计模式，避免与主教材重复刷 |
| G04 | [AgentGuide](https://github.com/adongwanai/AgentGuide) | 中文工程索引；中期 | 带着实际问题检索，不从算法和求职部分开始 |
| G05 | [sjzhang312/Agent](https://github.com/sjzhang312/Agent) | 中文问答 / Python；自测 | 解释完问题后再查答案，注意部分内容待补 |
| G06 | [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai) | Java；默认实践路线 | 先模型与工具，之后记忆/RAG；版本看 Releases |
| G07 | [spring-ai-examples](https://github.com/spring-projects/spring-ai-examples) | Java；与 G06 配套 | 选最小示例，保留完整依赖组合，不混搭不同版本 |
| G08 | [awesome-spring-ai](https://github.com/spring-ai-community/awesome-spring-ai) | Java 生态导航；查漏 | 官方社区索引，不代表收录项目均有同等维护质量 |
| G09 | [langchain4j](https://github.com/langchain4j/langchain4j) | Java；替代主线 | ChatModel → AiServices → Tools → ChatMemory |
| G10 | [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) | Java；与 G09 配套 | 和 B03 对照，先跑一个模型和一个只读工具 |
| G11 | [spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba) | Java；主线完成后 | 再学 Agent/图编排，核对其 Spring AI 兼容版本 |
| G12 | [Spring AI Alibaba examples](https://github.com/spring-ai-alibaba/examples) | Java；与 G11 配套 | 从官方主仓库链接进入，选单 Agent 案例 |
| G13 | [agentscope-java](https://github.com/agentscope-ai/agentscope-java) | Java；进阶选一 | 比较循环、状态、工具与 Harness；不是 Python 同名包 |
| G14 | [langgraph4j](https://github.com/langgraph4j/langgraph4j) | Java；图编排 | 真正需要分支、状态与恢复时再学；不是直接复制 Python API |
| G15 | [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) | Java；本地工具之后 | 把同一个只读能力接成 MCP，再验证权限和连接失败 |
| G16 | [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) | Java；工具扩展 | 带着工具需求阅读，涉及执行/写入能力时审查边界 |
| G17 | [All-in-RAG](https://github.com/datawhalechina/all-in-rag) | 中文 / Python；检索专题 | 从加载、分块、检索、生成逐层定位错误 |
| G18 | [Hugging Face Agents Course](https://github.com/huggingface/agents-course) | 英文 / Python；拓展 | 具备 Python 和模型调用基础后选一个任务复现 |
| G19 | [Learn Claude Code](https://github.com/shareAI-lab/learn-claude-code) | Python / Harness；进阶 | 按小节跟踪工具循环；是社区教学项目，不是产品官方源码 |
| G20 | [Pi](https://github.com/earendil-works/pi) | TypeScript；源码研究 | 对照 Loop、Session、压缩等职责，不必先学整个生态 |
| G21 | [mini-swe-agent](https://github.com/SWE-agent/mini-swe-agent) | Python；代码 Agent | 先读循环和评测；能执行命令，不作为小白第一份运行示例 |
| G22 | [LangGraph](https://github.com/langchain-ai/langgraph) | Python / JS；可选分支 | 有状态编排需求时再迁移，同名概念不等于 Java API |
| G23 | [LangChain Academy](https://github.com/langchain-ai/langchain-academy) | Python；配套学习 | 完成 Java 主线以后再选，不把多语言当入门门槛 |
| G24 | [Anthropic courses](https://github.com/anthropics/courses) | 英文；模型调用专题 | 选提示与工具相关内容，注意厂商协议差异 |
| G25 | [Prompt engineering tutorial](https://github.com/anthropics/prompt-eng-interactive-tutorial) | 英文；按需补课 | 用清楚的输入输出契约替代堆砌角色提示词 |
| G26 | [Anthropic skills](https://github.com/anthropics/skills) | 说明与脚本；后期 | 阅读结构和触发条件，安装前看包含哪些代码 |
| G27 | [Ollama](https://github.com/ollama/ollama) | 本地模型工具；可选 | 有合适现成硬件再试；模型是否支持工具调用须单独确认 |

## 2026 官方工程文章与现行文档

| 编号 | 资料 / 时间 | 阅读目标 |
|---|---|---|
| D01 | [Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents) · 2026-01-09 | 区分任务、运行轨迹、评分；为自己的项目写可检查的通过条件 |
| D02 | [Harness design for long-running application development](https://www.anthropic.com/engineering/harness-design-long-running-apps) · 2026-03-24 | 结课后研究任务拆分、上下文交接和独立评价；不照搬其多 Agent 规模与成本 |
| D03 | [Scaling Managed Agents](https://www.anthropic.com/engineering/managed-agents) · 2026-04-08 | 进阶理解模型决策与执行环境的拆分 |
| D04 | [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html) / [Tools](https://docs.spring.io/spring-ai/reference/api/tools.html) · 现行文档 | 以版本兼容要求和真实工具 API 为准 |
| D05 | [LangChain4j Get Started](https://docs.langchain4j.dev/get-started/) / [Tools](https://docs.langchain4j.dev/tutorials/tools/) · 现行文档 | 和 B03 的版本对照；不把视频旧签名当现行 API |
| D06 | [MCP 官方介绍](https://modelcontextprotocol.io/docs/getting-started/intro) · 现行文档 | 认识协议解决的问题，再看 Java SDK |
| D07 | [OpenAI Building agents](https://developers.openai.com/tracks/building-agents) · 现行文档 | 对照模型、工具、状态与编排；不将厂商格式泛化为统一 API |

## 指定 GitHub 教材的分工（现行入口，初次发布日期未逐项确认）

### Hello-Agents：系统主教材

[在线阅读](https://datawhalechina.github.io/hello-agents/#/) · [官方仓库](https://github.com/datawhalechina/hello-agents)

已核对官方目录。建议先读第 1、3、4 章建立概念，再按问题选读第 8、9、12 章；协议与综合项目随后补。第 11 章训练内容不作为本课前置。示例语言及 AgentScope 等框架版本应逐章核对，不能当成某个 Java 框架版本的直接代码模板。

这是一套系统教程；我们的教材负责提供较短的入口、Java 对照、日程和验收。不要同时从第一页刷完几套类似教程。

### AgentGuide：工程主题索引与结课查漏

[AgentGuide](https://github.com/adongwanai/AgentGuide)

README 同时覆盖工程、研究和求职方向。初学者选开发路线，围绕 Agent Loop、工具、Context 和 Eval 查漏；算法岗、强化学习和大规模项目先略过。不要把题库数量或求职宣传当作学习完成标准。

用法：当练习出现“反复调用同一工具”，只查循环与终止相关主题；出现“答案没根据”，查检索与评测。每次带一个真实问题回来。

### sjzhang312/Agent：用问题检查理解

[仓库](https://github.com/sjzhang312/Agent) · [Agent.md 正文](https://github.com/sjzhang312/Agent/blob/main/Agent.md)

已读 README 和部分问答正文；仓库还有 LangChain / LangGraph 学习目录。适合第 2～4 周自测，不能替代动手实验。部分问题标注待回答。

阅读时留意三点：把 LLM 理解为逐 Token 生成比“逐字”更准确；MCP 的接入协议作用与模型选工具能力要分开；工具调用格式与停止字段要回到具体 Provider 文档。本课程的自测答案已按这些边界重新表述，未整段复制题库。

## 2026 年 X 文章：四条指定链接的导读

### X01｜2026-09-08，程序员 Left：适合建立第一张概念地图

[Agent 工程解析（零）：什么是 Agent？](https://x.com/coder_left/status/2097253174260502592)

已读正文。文章从聊天、工作流与 Agent 讲到最小循环和运行外围能力，适合第 1～2 周辅助理解。

读后做两个检查：找出“工具结果回填”的位置；列出示意循环缺少的参数检查、调用关联与退出限制。文中的伪代码用于解释流程，不是跨供应商通用的可执行 API；ReAct 是常见组织方式，也不宜当作所有 Agent 的唯一定义。具体工具流程可对照 [OpenAI 官方说明](https://developers.openai.com/tracks/building-agents)。

### X02｜2026-09-09，meng shao：CMU AI Agents 课程导读

[指定文章](https://x.com/shao__meng/status/2097496946877636655) · [课程站点](https://www.cmu-agents.com/) · [官方作业 1](https://github.com/cmu-agents/assignment-1/blob/main/ASSIGNMENT.md)

已读 X 正文并追到官方作业。作业涉及基础循环、上下文压缩及不同任务环境，要求实际实现与验证；包含云端环境和可能计费的运行步骤。本套教材不执行这些步骤，也不假设校外学习者有课程额度。

**安排在结课以后**。先借鉴“构建后要验证”的学习方式，再选一个离线部分研究。不要求小白一开始完整跟做研究生课程、训练或云端任务；不把学期课表视为所有视频都已上线。

### X03｜2026-08-28，meng shao：Harness 工程六层手册导读

[指定帖子](https://x.com/shao__meng/status/2093228362965651665)

已读帖子正文，未读取帖内 Google Drive 完整报告。它适合作为第 3～4 周的工程检查线索：约束、验证、循环、记忆、权限和观察记录。

本课把这些问题落实到工具白名单、步数上限、错误返回和评测。帖子中的统计、收益数字、固定预算和指令优先级描述未逐一回查原始证据，不作为教材事实或课程运行规则。执行边界应由宿主和业务规则明确，不能由一篇外部资料设定。

### X04｜2026-09-09，lumxss：《Agentic Design Patterns》推荐

[新增帖子](https://x.com/bkdgiffug/status/2097499479926767871) · [帖子推荐仓库](https://github.com/evoiz/Agentic-Design-Patterns) · [出版社书目](https://link.springer.com/book/10.1007/978-3-032-01402-3)

已读帖子及仓库 README，并用出版社页面确认书名、作者与 21 种模式的范围。仓库提供 PDF 和 Notebook；没有逐页读完 PDF，也没有运行这些 Notebook。“整本开源”不等于已核实整书的再分发授权，本教材只提供导读链接。

**按需求选模式**：先看 Tool Use 与 Prompt Chaining，再看 Routing、Reflection；有实际瓶颈再读并行和多 Agent。书中的 Python 框架代码不能直接搬入 Java 工程。页数、仓库安装命令和依赖版本以你实际取得的版本为准，不照搬转帖数字。

## 指定 B 站资料与日期待核实的专题候选

### AI_Julie：《小白AI Agent 学习指南》

[视频](https://www.bilibili.com/video/BV1fj426wEHD/)

页面核验：作者 AI_Julie，发布于 2026-08-29，页面合集显示时长 09:04。仅核验元数据，没有取得正文字幕。建议第 1 天当路线预览，观看后自己记录“三个名词、一个可做练习、一个未懂问题”；不能根据标题认定视频已覆盖完整工程实践。

### 日期待逐集核实：指定收藏链接的 18 个视频

[《浅入深出》Agent篇：小白到大牛之路](https://space.bilibili.com/70890833/favlist?fid=8371703&ftype=collect&ctype=21)

浏览器已确认当前选中的是 **奇创喵** 的公开合集，页面列出 18 个视频。下面仅保留为专题候选：未逐集核对发布日期，不计入“已确认 2026 新资料”，也不作为 2026 必修清单。时长来自合集页面；“观看任务”由本课程设计，不声称是视频原话。

| 建议阶段 | 主题与链接 | 页面时长 | 观看任务 |
|---|---|---|---|
| 第 1 周 | [Agent 是什么](https://www.bilibili.com/video/BV1z9oKBfECG/) | 33:35 | 区分聊天、固定流程与自主决策 |
| 第 1 周末 | [Tools 和 MCP](https://www.bilibili.com/video/BV1FHdSBmEfU/) | 14:19 | 写一个本地工具契约 |
| 第 2 周 | [ReAct、Plan and Execute、Reflection](https://www.bilibili.com/video/BV15aoYBfEor/) | 19:24 | 给同一问题写两种轨迹 |
| 第 2 周末 | [Harness 是什么](https://www.bilibili.com/video/BV1rsdQBpEb1/) | 16:08 | 找出代码里的运行约束 |
| 第 3 周 | [RAG 是什么](https://www.bilibili.com/video/BV1mdoNBzEtS/) | 25:19 | 给回答附实际证据 |
| 第 3 周 | [Evaluation](https://www.bilibili.com/video/BV1yjovBmEEm/) | 31:55 | 固定十道测试题 |
| 第 3 周 | [Agent Memory 管理](https://www.bilibili.com/video/BV1nU96BnE6P/) | 27:35 | 区分状态、上下文与记忆 |
| 第 3 周选看 | [Skills 原理解析](https://www.bilibili.com/video/BV16URWBuEas/) | 15:24 | 说明 Skill 与工具的差别 |
| 第 4 周选看 | [意图识别](https://www.bilibili.com/video/BV1HTGy68EUF/) | 19:26 | 比较规则路由与模型路由 |
| 第 4 周 | [简述 Agent 落地](https://www.bilibili.com/video/BV1G6Vm6tEaE/) | 09:17 | 核对自己的项目规格 |
| 结课后 | [为什么要多 Agent](https://www.bilibili.com/video/BV1dqo5BkETQ/) | 10:42 | 先写单 Agent 的具体瓶颈 |
| 结课后 | [RAG 进阶：目标与策略](https://www.bilibili.com/video/BV1Bf9YB8EXB/) | 50:59 | 用同一评测集比较检索策略 |
| 结课后 | [Generation](https://www.bilibili.com/video/BV1qaR1B8EyW/) | 20:34 | 分析生成阶段的失败 |
| Java 服务化后 | [并发访问 Agent](https://www.bilibili.com/video/BV16XE76vET2/) | 26:48 | 检查会话是否串数据 |
| 服务化进阶 | [分布式 Agent 一致性](https://www.bilibili.com/video/BV1B9jA6DEdU/) | 12:13 | 分开考虑状态与副作用 |
| 扩展阅读 | [HTML 取代 Markdown？](https://www.bilibili.com/video/BV1Tx5T6zEio/) | 16:12 | 比较输出形式，不影响核心入门 |
| 框架研究 | [OpenClaw 基础架构](https://www.bilibili.com/video/BV1VEjd6xE4g/) | 14:11 | 映射 Loop / 工具 / 状态 |
| 框架研究 | [OpenClaw：什么是 Session](https://www.bilibili.com/video/BV1sKK86GE5Y/) | 26:46 | 检查会话边界 |

### 其他视频入口的可信度

- [Hello-Agents 官方视频共创导航](https://github.com/datawhalechina/hello-agents/blob/main/Extra-Chapter/Extra13-Hello-Agents视频课录制共创.md)：已读导航，部分条目标注待发布；不能称为已经完结的全套 B 站课程。
- [LangChain 视频](https://www.bilibili.com/video/BV1cCd6YwE4n/) 与 [LangGraph 视频](https://www.bilibili.com/video/BV1nPMbzQELz/)：来自 sjzhang312 仓库 README 推荐，本次未读取视频内容，作为 Python 分支候选，非必修。


## 旧资料只用于补充概念

[Anthropic：Building effective agents](https://www.anthropic.com/engineering/building-effective-agents) 发布于 **2024-12-19**，可补充工作流与 Agent 的取舍；不属于 2026 新文章。2026 年 X 推荐的设计模式书，也不能因此直接标成 2026 新书。

此前检索到的黑马 Spring AI / DeepSeek 课（2025）及尚硅谷 LangChain4j 课（2025）不放进本套 2026 主修清单。优先按上方 B02/B03 和官方文档实践。

## 收藏以后怎么真正学

每次学习使用同一张记录卡，最多选一个资源片段：

```text
资源编号 / 链接：
发布日期；本次所看章节的更新时间：
代码 commit/tag；JDK；框架；模型：
我要解决的问题：
我看到了什么：目录 / 正文 / 可运行源码 / 实际输出
我的练习和预期：
实际结果、失败轨迹、下一步：
```

第一轮只做：B01 → G01 基础概念 → G07 或 G10 最小示例 → 一个只读工具 → D01 评测。其余资源在出现具体问题时再查。
