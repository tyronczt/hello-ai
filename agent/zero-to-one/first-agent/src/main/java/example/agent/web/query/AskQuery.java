package example.agent.web.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档助手 HTTP 提问入参；问题由调用方在 POST 请求中提供。
 *
 * @param question 本次要查询的项目文档问题，长度为 1～1000 个字符
 */
public record AskQuery(@NotBlank @Size(max = 1000) String question) {}
