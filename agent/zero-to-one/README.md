# Javaer 从零学习 Agent：2026 学习包

> 整理与核验：2026-09-09。面向会 Java、了解基本 HTTP / JSON、没有 Agent 经验的开发者。整套内容独立使用，不依赖任何现有业务工程。

**主线：LLM 基础 → 工具调用 → Agent Loop → RAG 与记忆 → 评测 → MCP / Skills → 工程化。** 默认选择 Spring AI 做 Java 实践；偏好声明式接口的同学可以选择 LangChain4j，两条路线选一条即可。

## 今天先做什么

1. 用 10 分钟看 [AI_Julie：小白 AI Agent 学习指南，2026-08-29](https://www.bilibili.com/video/BV1fj426wEHD/)，记下三个不懂的词。
2. 阅读 [01 基础概念](01-foundations.md)，解释“谁决定下一步，谁执行工具”。
3. 运行 [02 离线 Java 实验](02-agent-loop-lab.md)，观察成功、无匹配、工具失败和超限。JDK 21 即可，无需 Maven、API Key 或 GPU。

## 学习资料目录

| 顺序 | 资料 | 学习产出 |
|---|---|---|
| 1 | [基础概念与第一堂实验](01-foundations.md) | 术语解释、纸上工具轨迹 |
| 2 | [Agent Loop 与 Java 实验](02-agent-loop-lab.md) | 可运行的离线机制验证 |
| 3 | [RAG、记忆、权限与可靠性](03-retrieval-and-reliability.md) | 工具契约、引用和错误处理 |
| 4 | [28 次学习安排与毕业练习](04-study-plan.md) | 每课任务、10 道验收题、复盘模板 |
| 5 | [2026 跨平台资源库](05-resources.md) | B 站、GitHub、X 的日期、阶段与选学建议 |
| 6 | [22 道自测题与参考答案](06-self-check.md) | 查出概念与工程盲点 |
| 实验 | [AgentLoopDemo.java](AgentLoopDemo.java) | 单文件、无外部依赖的循环模拟器 |

## 2026 资料怎么选

资源库区分三种时间证据：**2026 年实际发布**、**2026 年查阅的现行文档/仓库**、**经典或日期未确认资料**。查阅日期不等于发布日期；视频上传于 2026 年也不保证使用最新 API。

优先学习这五组，不必把所有收藏看完：

| 用途 | 首选 | 学到哪里就停下来做练习 |
|---|---|---|
| 概念入门 | [Hello-Agents](https://github.com/datawhalechina/hello-agents) + [Left 的 2026-09-08 文章](https://x.com/coder_left/status/2097253174260502592) | 能解释工具结果怎样回到模型 |
| Java 基础 | [微软 Java 生成式 AI 教材](https://github.com/microsoft/Generative-AI-for-beginners-java) | 模型请求、消息、结构化输出与工具 |
| Spring 路线 | [Spring AI 官方示例](https://github.com/spring-projects/spring-ai-examples) + 资源库 B02 的 2026 视频 | 最小对话 → 一个只读工具 |
| LangChain4j 替代路线 | [官方示例](https://github.com/langchain4j/langchain4j-examples) + [动力节点 2026-07-07 课程](https://www.bilibili.com/video/BV1DDMt6HEA7/) | AiServices、记忆隔离、Tools |
| 评测 | [Anthropic：Demystifying evals for AI agents，2026-01-09](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents) | 给自己的助手设计固定问题和通过条件 |

前两周不用先学 Python、深度学习推导、模型训练或多 Agent。后续按任务需要补：缺依据学 RAG，长任务失控学 Harness，连接外部能力学 MCP。会使用 Coding Agent 与会开发 Agent 是两种练习，不能互相替代。

## Java 环境与版本选择

离线实验建议 JDK 21。真实项目使用所选官方示例声明的 JDK / Spring Boot / 框架组合，保存 commit 或 tag。当前 [Spring AI 入门文档](https://docs.spring.io/spring-ai/reference/getting-started.html) 的 2.0.x 路线对应 Spring Boot 4.0.x / 4.1.x；不能把使用 Boot 3 的旧课 POM 与该路线直接混用。LangChain4j 的要求独立查 [官方入门文档](https://docs.langchain4j.dev/get-started/)。

每次学习 60～90 分钟，28 次学习可安排为连续 4 周，或每周 3～4 次、约 8 周。以能解释、复现和定位错误为验收，不以刷完视频为验收。

## 交付与验证边界

随教材提供的是**预设决策的控制流模拟器**，不调用真实 LLM，不读取实际文档。真实模型接入和毕业项目是学习任务，需要你按所选框架完成并评测。API 用量可能计费，按自己的账户预算开展。

本套资料保留来源链接和原创导读。B 站只核验页面/目录，未完整观看或取得字幕；GitHub 示例未全部运行。离线 Java 验证命令见第 02 章。
