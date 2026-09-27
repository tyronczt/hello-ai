package example.conversation.web;

import example.conversation.service.ConversationAgent;
import example.conversation.service.ConversationAgent.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 本地教学 API：会话 ID 仅作消息分组，不声称具备身份认证。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ConversationAgent agent;
    public ChatController(ConversationAgent agent) { this.agent = agent; }

    @PostMapping("/stateless")
    public Result stateless(@Valid @RequestBody Question input) {
        return agent.askStateless(input.question());
    }

    @PostMapping("/sessions/{id}")
    public Result ask(@PathVariable String id, @Valid @RequestBody Question input) {
        return agent.ask(id, input.question());
    }

    @DeleteMapping("/sessions/{id}")
    public Result clear(@PathVariable String id) { return agent.clear(id); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result invalid(IllegalArgumentException ex) {
        return new Result("INVALID_ARGUMENT", ex.getMessage(), java.util.List.of());
    }

    public record Question(@NotBlank @Size(max = 1000) String question) {}
}
