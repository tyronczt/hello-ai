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

/** 只负责 Query、DTO、VO 转换；工具循环和停止条件由 DocAgent 处理。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final DocAgent agent;

    public AgentController(DocAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/ask")
    public AskVO ask(@Valid @RequestBody AskQuery query) {
        var result = agent.process(new AskDTO(query.question()));
        return new AskVO(result.status().name(), result.answer(), result.trace());
    }
}
