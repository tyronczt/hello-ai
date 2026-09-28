package example.conversation.web;

import example.conversation.service.ConversationAgent;
import example.conversation.service.ConversationAgent.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 本地教学的同步聊天 API，提供无状态提问、按 ID 分组的多轮提问与历史清空。
 * 会话 ID 仅用于消息分组，没有身份认证或会话归属检查。
 * 正常方法返回的 STOPPED 仍是 HTTP 200 响应体，调用方必须检查 status。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    /** 执行框架工具循环并管理进程内会话历史的应用服务。 */
    private final ConversationAgent agent;
    /**
     * 注入聊天应用服务；HTTP 层不自行实现模型或工具循环。
     *
     * @param agent Spring 注入的会话 Agent
     */
    public ChatController(ConversationAgent agent) { this.agent = agent; }

    /**
     * 处理 POST /api/chat/stateless，从 JSON 请求体读取问题，不保留跨请求历史。
     * 问题必须非空白且不超过 1000 个字符；JSON 绑定与 Bean Validation 错误由框架处理。
     *
     * @param input 经请求体绑定和校验的问题
     * @return 本轮候选回复或停止原因，以及独立的模型和工具轨迹
     */
    @PostMapping("/stateless")
    public Result stateless(@Valid @RequestBody Question input) {
        return agent.askStateless(input.question());
    }

    /**
     * 处理 POST /api/chat/sessions/{id}，从路径读取会话 ID、从 JSON 请求体读取本轮问题。
     * 会话繁忙或达到上限时返回 STOPPED；成功回复也可能是等待用户补充的澄清追问。
     * ID 非法时由本控制器的异常处理器转换为 HTTP 400，不将 ID 视为认证身份。
     *
     * @param id 路径中的 1～64 位 ASCII 字母、数字、下划线或连字符分组标识
     * @param input 经 JSON 绑定与校验的非空白问题，长度上限为 1000 个字符
     * @return 本轮候选回复或停止结果，历史由应用服务按 ID 隔离
     * @throws IllegalArgumentException 会话 ID 不合法时由应用服务抛出
     */
    @PostMapping("/sessions/{id}")
    public Result ask(@PathVariable String id, @Valid @RequestBody Question input) {
        return agent.ask(id, input.question());
    }

    /**
     * 处理 DELETE /api/chat/sessions/{id}，清空对应历史但不释放已登记会话名额。
     * 空会话返回 CLEARED，运行中的会话返回 STOPPED；非法 ID 转换为 HTTP 400。
     *
     * @param id 路径中的会话分组标识，不提供归属授权依据
     * @return 清空结果或会话繁忙的停止结果，均不包含执行轨迹
     * @throws IllegalArgumentException 会话 ID 为 null 或格式不合法时由应用服务抛出
     */
    @DeleteMapping("/sessions/{id}")
    public Result clear(@PathVariable String id) { return agent.clear(id); }

    /**
     * 将应用层输入校验抛出的 IllegalArgumentException 转换为 HTTP 400。
     * 仅处理该异常类型，不替代框架对请求体绑定及 Bean Validation 错误的处理。
     *
     * @param ex 本次处理过程中抛出的参数校验异常
     * @return status 为 INVALID_ARGUMENT、answer 为异常说明且轨迹为空的结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result invalid(IllegalArgumentException ex) {
        return new Result("INVALID_ARGUMENT", ex.getMessage(), java.util.List.of());
    }

    /**
     * 两个 POST 入口共用的 JSON 请求体，只携带本轮问题，不携带身份或历史消息。
     *
     * @param question 必须非 null 且非空白，字符串长度不超过 1000 个字符
     */
    public record Question(@NotBlank @Size(max = 1000) String question) {}
}
