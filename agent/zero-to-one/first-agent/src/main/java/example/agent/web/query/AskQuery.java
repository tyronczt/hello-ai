package example.agent.web.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档助手 HTTP 提问入参；问题由调用方在 POST 请求中提供。
 * 仅定义绑定与校验约束，不在 record 构造时主动执行 Bean Validation。
 *
 * @param question 本次文档问题，必须非 null、非空白且长度不超过 1000 个字符
 */
public record AskQuery(@NotBlank @Size(max = 1000) String question) {}
