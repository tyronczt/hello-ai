package example.agent.web.vo;

import java.util.List;

/**
 * 文档助手 HTTP 响应；客户端应同时检查处理状态和依据记录。
 * 字段来自本次 AgentResultDTO；此对象不额外校验字段或复制 trace，当前调用方传入不可变轨迹。
 *
 * @param status COMPLETED 为候选答案；STOPPED 为未完成，不能当作已回答
 * @param answer 候选答案或停止原因；文档依据仍需核对
 * @param trace 本次请求的模型与工具轨迹；教学资料固定公开才返回工具正文，无轨迹时为空列表
 */
public record AskVO(String status, String answer, List<String> trace) {}
