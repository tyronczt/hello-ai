package example.agent.dto;

import java.util.List;

/**
 * Agent 的处理结果；完成只表示循环得到候选答案，不表示事实已自动核验。
 *
 * @param status COMPLETED 表示得到候选答案；STOPPED 表示预算、工具或模型错误使任务停止
 * @param answer 候选答案或停止原因
 * @param trace 本次请求的模型轮次与工具执行记录，不含其他用户请求的轨迹
 */
public record AgentResultDTO(Status status, String answer, List<String> trace) {
    public enum Status { COMPLETED, STOPPED }

    public AgentResultDTO {
        trace = List.copyOf(trace);
    }
}
