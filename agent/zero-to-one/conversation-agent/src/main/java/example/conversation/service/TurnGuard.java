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

/**
 * 在每轮提问的工具循环内检查模型次数、工具整批请求和轮次间耗时，并记录轨迹。
 * 每轮创建一个实例，不保存跨轮计数；具体工具执行和下一次模型调用由框架负责。
 * 耗时只在模型调用前后检查，不能中断正在阻塞的网络调用。
 */
final class TurnGuard implements CallAdvisor {
    /** 每轮最多请求模型 6 次，最后一次不允许再发起工具执行。 */
    private static final int MAX_MODEL_CALLS = 6;
    /** 每轮最多批准 8 个工具请求，同一模型响应中的请求整批计入。 */
    private static final int MAX_TOOL_CALLS = 8;
    /** 本教学 Agent 允许调用的固定只读工具名称。 */
    private static final Set<String> ALLOWED = Set.of("searchDocs", "readDoc");
    /** 本轮的轨迹列表，与本轮工具接收器共用，不包含其他轮次记录。 */
    private final List<String> trace;
    /** 返回单调纳秒读数的时钟，测试时可注入控制时间。 */
    private final LongSupplier clock;
    /** 创建本轮检查器时的纳秒读数，是耗时预算的起点。 */
    private final long started;
    /** 本轮已发起的模型调用次数，在每次进入下游链之前递增。 */
    private int modelCalls;
    /** 本轮通过整批预算与工具名称检查的工具请求累计数。 */
    private int toolCalls;

    /**
     * 绑定本轮轨迹与时钟，并在创建时开始耗时计量。
     *
     * @param trace 接收本轮模型和工具请求事件的列表
     * @param clock 返回单调纳秒读数的非 null 时钟
     */
    TurnGuard(List<String> trace, LongSupplier clock) {
        this.trace = trace;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    /**
     * 提供框架识别本检查器的固定名称。
     *
     * @return turn-budget-and-trace
     */
    @Override public String getName() { return "turn-budget-and-trace"; }

    /**
     * 返回工具循环 Advisor 之后的排序值，使每次循环内模型请求经过本检查器。
     *
     * @return ToolCallingAdvisor.DEFAULT_ORDER 加 100 的排序值
     */
    @Override public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 100; }

    /**
     * 在模型调用前后检查本轮预算，并在返回工具响应前预检整批工具请求。
     * 禁止未开放工具、部分超额执行和最后一轮无机会回填的工具执行。
     * 没有工具请求时必须有非空白回复；通过检查后由外层框架继续处理。
     *
     * @param request 框架准备的本次模型请求，包含当前工具循环的消息
     * @param chain 下游模型调用链，本方法每次通过检查后调用一次
     * @return 通过结果、预算与工具名称检查的框架响应
     * @throws Stopped 预算耗尽、工具名称未开放、响应缺失或最终回复为空白时抛出
     */
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

    /**
     * 检查从本轮检查器创建起是否达到五分钟，不取消已经发出的模型请求。
     *
     * @throws Stopped 已用纳秒数大于或等于五分钟时抛出
     */
    private void checkTime() {
        if (clock.getAsLong() - started >= Duration.ofMinutes(5).toNanos())
            throw new Stopped("任务耗时超出预算，尚未完成。");
    }

    /** 预算或响应预检的停止信号，由 ConversationAgent 转换为 STOPPED 结果。 */
    static final class Stopped extends RuntimeException {
        /**
         * 保存对调用方可展示的停止原因，不携带模型异常消息或工具正文。
         *
         * @param message 预算、工具权限或响应预检产生的安全停止提示
         */
        Stopped(String message) { super(message); }
    }
}
