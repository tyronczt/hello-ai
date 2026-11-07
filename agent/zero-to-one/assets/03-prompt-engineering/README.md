# 提示词工程配图与制作说明

本目录保存[第 03 篇](../../03-prompt-engineering.md)的既有素材与制作文件。当前正文共使用 9 张图：7 张位于 [diagram](../../diagram/) 的 SVG，以及本目录的两张 PNG。图号按正文出现顺序排列。

| 当前使用的素材 | 正文位置 | 解释范围 |
|---|---|---|
| [prompt-versions.png](prompt-versions.png) | 4.2，图 7 | BASE、RULES、EXAMPLES 的累计关系；图中 CLI 参数需映射到 5.1 的 HTTP 字段 |
| [controlled-evaluation.png](controlled-evaluation.png) | 5.2，图 9 | 固定条件、保留记录、核对结果；不表示天气入口已实现冻结快照评估 |

当前图 1～6、图 8 对应 diagram 中的任务流程、任务契约、自回归生成、注意力、长期规则、天气工具边界与三层核验图。图 3 是生成总览，图 4 展开模型计算。

## 素材依据与历史边界

- [WeatherPrompts.java](../../weather-agent/src/main/java/example/weather/agent/WeatherPrompts.java)：当前天气提示词组合。
- [WeatherAgent.java](../../weather-agent/src/main/java/example/weather/agent/WeatherAgent.java)：模型循环、工具预检与执行。
- [WeatherContext.java](../../weather-agent/src/main/java/example/weather/agent/WeatherContext.java)：首轮与工具回填后的消息。
- [WeatherTools.java](../../weather-agent/src/main/java/example/weather/tool/WeatherTools.java)：高德字段适配与地点 ID 边界。

`request-anatomy.svg/png`、`weather-three-rounds.svg/png` 及其生成脚本属于旧稿素材，当前正文未引用。部分文字保留了旧数据源时期的“当前条件 / 三天预报”，不适用于现在的高德预报契约；复用前需要重新核对，不能据此说明当前接口能力。

`prompt-and-execution.png`、`failure-handling-branches.png`、`layered-prompt-input.png` 当前也未被正文使用。保留文件与[原有制作记录](production-notes.md)，本轮仅订正文档索引，没有重新生成、裁切或覆盖图像。

## 既有生成脚本

`build-assets.cjs` 可重新生成本目录三张历史技术图的 SVG：

```powershell
node .\build-assets.cjs
```

增加 `--png` 时还需可被 Node.js 找到的 sharp 和中文字体。既有导出使用 Microsoft YaHei、144 DPI；SVG 保留文字，PNG 固定渲染结果。此脚本不会生成正文 diagram 中的七张 SVG，也不会自动将旧图改成当前字段。

## 阅读边界

图示通常是教学简化，不是完整 HTTP 抓包。工具定义不等于一条消息，工具请求不等于已经执行，COMPLETED 不等于事实核验通过。三轮调用只是单地点、两个工具依次成功的一条路径；实际过程以任务报告为准。
