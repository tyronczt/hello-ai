package example.conversation.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/** 每次用户提问创建一个实例；只检查与记录，不执行工具，也不重新实现循环。 */
final class TurnGuard implements CallAdvisor {
    private static final int MAX_MODEL_CALLS = 6;
    private static final int MAX_TOOL_CALLS = 8;
    private static final Set<String> ALLOWED = Set.of("searchDocs", "readDoc");
    private final List<String> trace;
    private final LongSupplier clock;
    private final long started;
    private int modelCalls;
    private int toolCalls;

    TurnGuard(List<String> trace, LongSupplier clock) {
        this.trace = trace;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    @Override public String getName() { return "turn-budget-and-trace"; }

    // 位于工具循环内部：每一次模型请求都经过此处。
    @Override public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 100; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        checkTime();
        if (modelCalls >= MAX_MODEL_CALLS) throw new Stopped("模型调用预算耗尽。");
        modelCalls++;
        trace.add("模型调用 " + modelCalls + "/" + MAX_MODEL_CALLS
                + "，消息数=" + request.prompt().getInstructions().size());
        var response = chain.nextCall(request);
        checkTime();
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null) {
            throw new Stopped("模型未返回有效结果。");
        }
        var output = response.chatResponse().getResult().getOutput();
        var calls = output.getToolCalls();
        if (!calls.isEmpty()) {
            // 整批预检，避免先执行一部分后才发现超额；最后一轮不执行无机会回填的工具。
            if (modelCalls == MAX_MODEL_CALLS || calls.size() > MAX_TOOL_CALLS - toolCalls)
                throw new Stopped("剩余调用预算不足，尚未完成。");
            if (calls.stream().anyMatch(call -> !ALLOWED.contains(call.name())))
                throw new Stopped("模型请求了未开放的工具。");
            toolCalls += calls.size();
            calls.forEach(call -> trace.add("请求工具：" + call.name() + "，调用 ID：" + call.id()));
        } else if (output.getText() == null || output.getText().isBlank()) {
            throw new Stopped("模型返回空答案。");
        }
        // 返回给外层 ToolCallingAdvisor，由框架执行工具并发起下一次模型请求。
        return response;
    }

    private void checkTime() {
        if (clock.getAsLong() - started >= Duration.ofMinutes(5).toNanos())
            throw new Stopped("任务耗时超出预算，尚未完成。");
    }

    static final class Stopped extends RuntimeException {
        Stopped(String message) { super(message); }
    }
}
