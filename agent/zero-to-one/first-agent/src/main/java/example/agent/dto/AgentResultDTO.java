package example.agent.dto;

import java.util.List;

/**
 * Agent 的处理结果；完成只表示循环得到候选答案，不表示事实已自动核验。
 *
 * @param status COMPLETED 表示得到候选答案；STOPPED 表示输入、预算、工具或模型问题使任务停止
 * @param answer 候选答案或停止原因
 * @param trace 本次请求的模型轮次与工具执行记录；列表及元素均不得为 null，构造时复制
 */
public record AgentResultDTO(Status status, String answer, List<String> trace) {
    /** 任务结果类型；枚举值直接映射为 HTTP 响应的 status 字符串。 */
    public enum Status {
        /** 已获得模型候选答案，仍需核对资料依据。 */
        COMPLETED,
        /** 本次任务未完成，answer 保存停止原因。 */
        STOPPED
    }

    /**
     * 将轨迹复制为不可变快照，防止后续修改原列表改变已返回的结果。
     * 不额外校验 status 或 answer，调用方须按状态提供相应文本。
     *
     * @param status 本次任务状态
     * @param answer 候选答案或停止原因
     * @param trace 本次轨迹，列表及元素均不得为 null
     * @throws NullPointerException trace 为 null 或含 null 元素时抛出
     */
    public AgentResultDTO {
        trace = List.copyOf(trace);
    }
}
