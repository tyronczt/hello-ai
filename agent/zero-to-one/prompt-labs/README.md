# 提示词工程：两个离线 Java 实验

配合[提示词工程](../03-prompt-engineering.md)阅读。使用 JDK 21，无依赖、无密钥、无网络调用；不改变天气助手。

从仓库根目录运行：

```powershell
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' agent/zero-to-one/prompt-labs/SamplingLab.java
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' agent/zero-to-one/prompt-labs/EvidenceCheckLab.java
```

| 实验 | 观察与自检 | 结论边界 |
|---|---|---|
| `SamplingLab` | softmax、温度、top-p、贪心选择；10 个数值与参数边界检查 | logits 为人工指定，只验证计算，不代表真实模型对提示词的反应 |
| `EvidenceCheckLab` | 同一目标和证据下检查地点、日期、时区、来源、null、服务失败；12 个合成案例检查 | 输入已经是 Java record；没有 JSON 解析、自然语言提取、真实工具回放或模型质量评分 |

检查失败时抛出异常并以非零状态退出，不依赖 `-ea`。类中保留自检是为了让读者修改数据后立即观察边界，不是 Maven/JUnit 测试集。

第二个实验中的日期和地点仅用于合成夹具，降水概率未知；不能用作天气预报。它检查证据与候选是否一致，不能验证气象模型本身是否准确，也不评判自然语言建议。它的可用/不可用二分只覆盖预报核验；用户条件缺失时的追问需要另一组任务。

两份程序均只使用局部变量和不可变输入；没有共享可变状态。若接入实际服务，应沿用天气助手的每任务状态隔离，并另行处理解析、单位、时间有效性、网络失败及授权边界。
