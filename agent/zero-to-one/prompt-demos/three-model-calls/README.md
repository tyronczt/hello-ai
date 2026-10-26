# 三次模型调用交互示例

本示例对应[第 03 篇提示词工程](../../03-prompt-engineering.md)的 5.2 节「沿三次模型调用看信息怎样增加」，回放用户在 2026-10-26 于 IDEA Debug 完成的真实任务 `run-8066285691164643862`。本次使用 `rules` / `full`：先检索地点，再查询高德预报，最后组织候选回答。

双击本目录的 [index.html](index.html)，即可在浏览器中离线打开，无需启动 Java 程序或本地 Web 服务。使用页面上的“上一格”“下一格”“播放/暂停”和“重置”控件，观察代码位置、消息列表及当前步骤说明。

示例保留现有 14 格过程，包含 3 次模型调用和 2 次工具执行，三次请求中的消息数依次为 **2 → 4 → 6**。工具定义单独提供，不计入这些消息条数。14 格是对实际日志顺序的教学拆分，播放速度与原始耗时无关；每格可展开本步日志，工具结果和最终完整候选回答也可单独展开。

| 模型轮次 | 输入消息数 | 消息 JSON 长度（UTF-16 单元） | 模型调用耗时 | SDK Token（输入 / 输出 / 合计） |
|---|---|---|---|---|
| 1 | 2 | 744 | 2785 ms | 880 / 47 / 927 |
| 2 | 4 | 1570 | 951 ms | 1105 / 39 / 1144 |
| 3 | 6 | 3293 | 1961 ms | 1589 / 256 / 1845 |

表中长度、耗时和用量均来自本次报告。JSON 长度不是 Token 数，Token 也不用于估算费用、缓存命中或节省比例。输入消息条数在请求发起后显示；对应耗时和用量在回放到该轮模型返回后显示。

对应源码：

- [WeatherAgent.java](../../weather-agent/src/main/java/example/weather/agent/WeatherAgent.java)：组装请求、执行工具循环并保留运行记录。
- [WeatherContext.java](../../weather-agent/src/main/java/example/weather/agent/WeatherContext.java)：组织首轮消息和工具执行后的下一轮输入。
- [WeatherTools.java](../../weather-agent/src/main/java/example/weather/tool/WeatherTools.java)：检索地点、检查地点 ID 并取得天气数据。

实际城市搜索返回北京市 `110000` 和北京城区 `110100` 两个候选，模型使用本次返回的 `110000` 查询预报。`localTime=2026-10-26T22:18:18.705884+08:00`，因此「明天」对应 `2026-10-27`：白天晴、16°C，夜间晴、1°C，`precipitationProbability=null`。概率不能补为零或从天气文字推算；日夜温度也不等于最高、最低温度。预报发布时间为 `2026-10-26 22:03:50`，本次获取时间为 `2026-10-26T14:18:18.705884Z`。这些字段是项目整理后的工具结果，不是高德原始 JSON。

页面已嵌入本次 `report.json` 的消息视图、公开工具结果和用户日志节选，不需要读取本机报告文件，也不调用模型或天气接口。四条逐日记录对应本次实际返回的 10 月 26—29 日；它们是历史预报，不是打开页面时的实时天气。最终候选按原文保存在页面数据中，`COMPLETED` 表示非空候选已产生，不代表自动事实核验或提示词质量评估。

页面的 system 全文来自本次报告，保留当时使用的 `BASE + RULES`；后续修改 [WeatherPrompts.java](../../weather-agent/src/main/java/example/weather/agent/WeatherPrompts.java) 不会自动改变这份历史回放。消息是 Spring AI Prompt 视图，不是完整 HTTP 抓包。Agent、Service、HTTP 日志耗时分别为 9282、9513、16166 ms，范围不同，不能互相替代或用作性能基准。

用户日志通过 `--logging.pattern.console=%msg%n` 精简格式，隐藏了默认的 `requestId`。本页仅嵌入阶段日志，未保存启动命令中的 Key、本机 classpath 或调试器临时路径；离线播放无需模型或高德 Key。外部服务原始字段含义可查阅[高德天气查询](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)与[行政区域查询](https://lbs.amap.com/api/webservice/guide/api/district)，本次验证范围见[天气模块说明](../../weather-agent/README.md#已验证的范围)。
