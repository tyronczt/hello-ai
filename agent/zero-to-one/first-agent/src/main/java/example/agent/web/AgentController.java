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
    private final DocAgent agent;

    public AgentController(DocAgent agent) {
        this.agent = agent;
    }

    /** 校验本次问题并交给 Agent 处理；将应用层结果转换为 HTTP 响应。 */
    @PostMapping("/ask")
    public AskVO ask(@Valid @RequestBody AskQuery query) {
        // 用户的问题来自本次 POST；stage 和用户身份都不从请求体传给 Agent。
        var result = agent.process(new AskDTO(query.question()));
        // STOPPED 仍有结构化响应，调用方需检查 status，不能把停止原因当成答案。
        return new AskVO(result.status().name(), result.answer(), result.trace());
    }
}
