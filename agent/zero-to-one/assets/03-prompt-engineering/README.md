# 提示词工程配图与制作说明

此目录集中保存[提示词工程](../../03-prompt-engineering.md)的技术图、小天插图及制作文件。三张技术图由代码绘制，SVG 是可编辑源图，同名 PNG 用于正文展示；原有小天插图继续保留。技术图使用浅灰蓝背景、蓝灰细边框与深蓝文字分组和标注。

| 文件 | 图意 |
| --- | --- |
| `request-anatomy.svg` / `.png` | 首轮请求将 `system`、`user` 消息与独立工具配置一起提供给模型。工具定义不是消息，模型输出的工具请求也不等于已经执行。 |
| `weather-three-rounds.svg` / `.png` | `context=full` 下，单城市可确定且两个工具均成功的一条正常路径：3 轮模型调用、2 次工具执行，消息依次为 2、4、6 条。 |
| `prompt-versions.svg` / `.png` | `basic = BASE`、`rules = BASE + RULES`、`examples = BASE + RULES + EXAMPLES` 的累计实验关系。 |

图中文字与字段依据以下源码核对：

- [`WeatherPrompts.java`](../../weather-agent/src/main/java/example/weather/WeatherPrompts.java)：版本拼接与提示词节选。
- [`WeatherAgent.java`](../../weather-agent/src/main/java/example/weather/WeatherAgent.java)：模型调用、工具预检与执行边界。
- [`WeatherContext.java`](../../weather-agent/src/main/java/example/weather/WeatherContext.java)：首轮输入、完整历史和请求/结果配对。
- [`WeatherTools.java`](../../weather-agent/src/main/java/example/weather/WeatherTools.java)：工具参数与返回字段、地点 ID 范围校验。

## 原有小天配图复用

[正文](../../03-prompt-engineering.md)在使用三张技术图的同时，复用本目录中的三张小天配图。已逐张查看并核对图意，没有重新生成、裁切或覆盖图片文件。

| 文件 | 正文位置 | 解释范围 |
| --- | --- | --- |
| [prompt-and-execution.png](prompt-and-execution.png) | 2.3，图 2 | 提示词引导选择，程序检查执行；图中“权限”为通用边界，正文限定本项目实际检查范围。 |
| [failure-handling-branches.png](failure-handling-branches.png) | 5.4，图 5 | 四类独立缺口对应不同处理；不是四步串行流程，也不表示答案已经自动核验。 |
| [controlled-evaluation.png](controlled-evaluation.png) | 6.3，图 6 | 固定条件、保留记录与逐项核对；冻结工具快照是实验设计要求，当前 CLI 未实现真实模型批量重放。 |

三张技术图在正文中依次为请求组成图 1、版本比较图 3、三轮调用图 4。原有 `layered-prompt-input.png` 继续保留在本目录，旧稿的图片引用仍然有效；当前正文未插入此图，以减少与第 1 节请求结构图和职责表的近邻重复。原图生成过程见[原有制作记录](production-notes.md)。

## 复现

在本目录运行：

```powershell
node .\build-assets.cjs
```

此命令用 Node.js 标准库重新生成 3 张 SVG。导出 PNG 还需要可被 Node.js 找到的 `sharp`，并需要中文字体：

```powershell
node .\build-assets.cjs --png
```

如 `sharp` 位于独立运行时，先将该运行时的 `node_modules` 目录加入当前 PowerShell 进程的 `NODE_PATH`。本次导出使用 Microsoft YaHei 字体与 `sharp`，按 144 DPI 栅格化；不使用图像生成模型。SVG 保留文字，阅读端字体差异可能改变字形，PNG 固定了本次渲染结果。

## 解释边界

- 图示为 Spring AI `Prompt` 的结构说明，不宣称是最终 HTTP 请求的逐字记录；未显示全部选项和返回字段。
- `call_city`、`call_weather` 是调用 ID 占位。图中没有虚构天气数值或地点 ID，也没有执行真实模型或天气请求。
- 三轮查询只是正常路径。地点歧义、工具失败或预算限制可能改变流程。`getWeather` 依赖前一步实际返回的 ID，不能与此次城市搜索并行。
- `COMPLETED` 只表示应用拿到了候选回答，不能证明天气事实和建议已自动核验。
- 图中的版本节选来自源码，换行仅服务于图内阅读。版本是累计对照方案，不是执行阶段，也不预设后一个版本效果更好。
