# 三次模型调用交互示例

本示例对应[第 03 篇提示词工程](../../03-prompt-engineering.md)的 5.2 节“沿三次模型调用看信息怎样增加”，展示 `full` 模式下的一条正常路径：先检索地点，再查询天气，最后组织回答。

双击本目录的 [index.html](index.html)，即可在浏览器中离线打开，无需启动 Java 程序或本地 Web 服务。使用页面上的“上一格”“下一格”“播放/暂停”和“重置”控件，观察代码位置、消息列表及当前步骤说明。

示例保留 14 格过程，包含 3 次模型调用和 2 次工具执行，三次请求中的消息数依次为 **2 → 4 → 6**。工具定义单独提供，不计入这些消息条数。

对应源码：

- [WeatherAgent.java](../../weather-agent/src/main/java/example/weather/agent/WeatherAgent.java)：组装请求、执行工具循环并保留运行记录。
- [WeatherContext.java](../../weather-agent/src/main/java/example/weather/agent/WeatherContext.java)：组织首轮消息和工具执行后的下一轮输入。
- [WeatherTools.java](../../weather-agent/src/main/java/example/weather/tool/WeatherTools.java)：检索地点、检查地点 ID 并取得天气数据。

页面使用符合当前高德适配契约的合成数据：地点 ID `110000` 表示北京行政区，`localTime` 确定相对日期，`reportTime` 保留预报发布时间，`daily` 按日期对象组织日夜天气与温度。`precipitationProbability` 为 `null`，不能补为零或从天气文字推算；日夜温度也不等于最高、最低温度。字段是本项目整理后的工具结果，不是高德原始 JSON。

日期、温度、天气现象与答复均为教学设定，不是实时预报，也没有照搬用户提供的真实高德响应。示例中的四条逐日记录只服务这条演示路径，实际工具以服务返回的日期为准。播放只切换演示步骤，不调用任何模型或天气接口，也不导入真实 `report.json`。它仅解释 `full` 模式下无歧义、无重试的正常交互过程，不代表真实运行结果或提示词质量评估。

页面的 system 全文与 [WeatherPrompts.java](../../weather-agent/src/main/java/example/weather/agent/WeatherPrompts.java) 中的 `BASE + RULES` 保持一致；外部服务的原始字段含义参见[高德天气查询](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)与[行政区域查询](https://lbs.amap.com/api/webservice/guide/api/district)。播放无需模型或高德 Key。
