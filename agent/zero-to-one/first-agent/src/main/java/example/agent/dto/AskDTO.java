package example.agent.dto;

/**
 * Web 边界传给 Agent 的一次提问，不包含客户端自称的用户身份。
 * DTO 构造时不执行输入校验；HTTP 入口先校验 Query，DocAgent 再保护其他调用入口。
 *
 * @param question 本次原始问题；null、空白或超过 1000 个字符时由 DocAgent 返回 STOPPED
 */
public record AskDTO(String question) {}
