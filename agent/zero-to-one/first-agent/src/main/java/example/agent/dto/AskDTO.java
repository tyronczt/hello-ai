package example.agent.dto;

/**
 * Web 边界传给 Agent 的一次提问，不包含客户端自称的用户身份。
 *
 * @param question 用户本次提交的问题；每个请求独立处理，不自动继承其他请求的历史
 */
public record AskDTO(String question) {}
