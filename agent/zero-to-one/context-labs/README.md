# 上下文工程：KV Cache 数值实验

对应[第 04 篇上下文工程](../04-context-engineering.md)的实践二。其他实践复用 [first-agent](../first-agent/README.md) 和 [weather-agent](../weather-agent/README.md)，不再建立另一套 Agent 框架。

在本目录使用 JDK 21 运行：

```powershell
java '-Dfile.encoding=UTF-8' KvCacheLab.java
if ($LASTEXITCODE -ne 0) { throw 'KV Cache 实验失败' }
```

程序对固定四个二维输入运行两层单头因果注意力，比较每个前缀的完整重算与逐层 K/V 缓存。自检覆盖增量结果等价、追加不改变过去位置、修改前缀后旧结果失效，以及重建后的结果恢复一致。失败抛出 `AssertionError`，不依赖 `-ea`。

预期输出中的 K/V 向量投影次数为 `full=40, cached=16`。这不是性能基准：没有训练权重、位置编码、残差、MLP、GPU 或模型 API，也没有测量服务商缓存命中或网络延迟。固定数值实验使用 double 表示向量元素，不涉及金额。

每个 `Cache` 只用于单条序列，按顺序追加，不支持跨用户或并发复用。所有输入都是文件内的教学常量，不读取用户文件、环境密钥或外部服务。
