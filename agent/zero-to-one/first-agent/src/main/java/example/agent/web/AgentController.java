package example.agent.web;

import example.agent.service.DocAgent;
import example.agent.dto.AskDTO;
import example.agent.web.query.AskQuery;
import example.agent.web.vo.AskVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 边界只做参数校验及 Query、DTO、VO 转换；工具循环和停止条件由 DocAgent 处理。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    /** 处理本次提问的无会话 Agent；Controller 不保存问题或执行轨迹。 */
    private final DocAgent agent;

    /**
     * 注入应用层处理器；模型和工具循环由处理器负责。
     *
     * @param agent Spring 注入的文档 Agent
     */
    public AgentController(DocAgent agent) {
        this.agent = agent;
    }

    /**
     * 处理 POST /api/agent/ask，将 JSON Query 转为内部 DTO，并将结果转为 VO。
     * 问题必须非空白且不超过 1000 个字符；绑定或校验失败由 Spring MVC 返回错误。
     * 当前教学接口没有身份认证，资料为固定公开内容，不接受客户端指定阶段或身份。
     * COMPLETED 与 STOPPED 均作为正常响应体返回，调用方必须检查 status。
     *
     * @param query 经 JSON 绑定和 Bean Validation 校验的本次问题
     * @return 候选答案或停止原因，以及本次请求独立的模型和工具轨迹
     */
    @PostMapping("/ask")
    public AskVO ask(@Valid @RequestBody AskQuery query) {
        // 用户的问题来自本次 POST；stage 和用户身份都不从请求体传给 Agent。
        var result = agent.process(new AskDTO(query.question()));
        // STOPPED 仍有结构化响应，调用方需检查 status，不能把停止原因当成答案。
        return new AskVO(result.status().name(), result.answer(), result.trace());
    }
}
