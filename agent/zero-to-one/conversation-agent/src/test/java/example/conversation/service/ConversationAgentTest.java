package example.conversation.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import static org.junit.jupiter.api.Assertions.*;

/** 模型只返回脚本响应；真正执行 ChatClient、Advisor、工具和记忆组件，不连接外网。 */
class ConversationAgentTest {
    /**
     * 构造不请求工具的脚本模型回复，不调用真实模型服务。
     *
     * @param text 预设回复文本，可传空字符串以验证空答案停止分支
     * @return 包含一条助手回复的模型响应
     */
    static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
    /**
     * 构造同一响应中的工具请求批次，供名称与预算预检场景使用。
     *
     * @param name 每个请求使用的工具名称，可为未注册名称以验证拒绝分支
     * @param arguments 每个工具请求携带的 JSON 参数文本
     * @param count 批次请求数量，调用 ID 按零起始序号生成
     * @return 含工具请求且没有正文的脚本响应
     */
    static ChatResponse calls(String name, String arguments, int count) {
        var calls = IntStream.range(0, count).mapToObj(i ->
                new AssistantMessage.ToolCall("call-" + i, "function", name, arguments)).toList();
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(calls).build())));
    }
    /**
     * 创建脚本模型替身，保留 OpenAI 选项类型以实际运行框架工具循环。
     *
     * @param fn 根据实际 Prompt 返回预设响应或抛出模拟异常的脚本
     * @return 不联网的 ChatModel，仅将每次调用交给脚本
     */
    static ChatModel model(Function<Prompt, ChatResponse> fn) {
        return new ChatModel() {
            /**
             * 将框架的实际请求交给脚本函数。
             *
             * @param prompt 框架生成的消息与工具选项
             * @return 脚本预设的模型响应
             */
            @Override public ChatResponse call(Prompt prompt) { return fn.apply(prompt); }
            /**
             * 提供工具循环所需的 OpenAI 兼容默认选项类型。
             *
             * @return 新建的 OpenAiChatOptions，不包含真实密钥或网络调用
             */
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return org.springframework.ai.openai.OpenAiChatOptions.builder().build();
            }
        };
    }
    /**
     * 检查任一非 null 消息正文是否包含指定标记，不把工具消息类型当作正文依据。
     *
     * @param messages 当前请求消息或会话历史
     * @param text 要查找的标记文本
     * @return 任一消息正文包含标记时为 true
     */
    static boolean hasText(List<Message> messages, String text) {
        return messages.stream().anyMatch(m -> m.getText() != null && m.getText().contains(text));
    }

    /** 验证框架实际执行工具并回填结果，而会话只保留外层用户与最终助手消息。 */
    @Test void frameworkRunsToolsAndMemoryStoresOnlyOuterExchange() {
        List<Prompt> prompts = new ArrayList<>();
        var agent = new ConversationAgent(model(p -> {
            prompts.add(p);
            if (prompts.size() == 1) return calls("readDoc", "{\"docId\":\"pagination-v2\"}", 1);
            assertTrue(p.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance));
            return answer("pageSize 最大为 100，默认值未规定。来源 pagination-v2。");
        }));
        var result = agent.ask("a", "分页参数怎么传？");
        assertEquals("COMPLETED", result.status(), result.toString());
        assertEquals(2, prompts.size());
        assertTrue(result.trace().stream().anyMatch(s -> s.contains("readDoc 返回：")));
        assertTrue(hasText(agent.history("a"), "分页参数怎么传"));
        assertTrue(agent.history("a").stream().noneMatch(ToolResponseMessage.class::isInstance));
        assertTrue(agent.history("a").stream().filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast).noneMatch(AssistantMessage::hasToolCalls));
    }

    /** 验证同会话追问能看到标记，其他会话及无状态入口不会继承该标记。 */
    @Test void followupSeesOwnHistoryButOtherSessionAndStatelessDoNot() {
        List<Prompt> prompts = new ArrayList<>();
        var agent = new ConversationAgent(model(p -> { prompts.add(p); return answer("已收到。"); }));
        agent.ask("a", "请记住本轮标记 ALPHA_42");
        agent.ask("a", "刚才的标记是什么？");
        assertTrue(hasText(prompts.get(1).getInstructions(), "ALPHA_42"));
        agent.ask("b", "刚才的标记是什么？");
        assertFalse(hasText(prompts.get(2).getInstructions(), "ALPHA_42"));
        agent.askStateless("刚才的标记是什么？");
        assertFalse(hasText(prompts.get(3).getInstructions(), "ALPHA_42"));
    }

    /** 验证澄清追问作为已完成的一轮保留，用户补充与追问一起进入后续请求。 */
    @Test void clarificationIsAnOrdinaryCompletedTurnAndSupplementIsKept() {
        var count = new AtomicInteger();
        var agent = new ConversationAgent(model(p -> {
            if (count.incrementAndGet() == 1) return answer("请补充你使用的项目版本。");
            assertTrue(hasText(p.getInstructions(), "请补充你使用的项目版本"));
            assertTrue(hasText(p.getInstructions(), "我使用 v2"));
            if (count.get() == 2) return calls("readDoc", "{\"docId\":\"pagination-v2\"}", 1);
            return answer("v2：pageSize 最大 100，默认值未规定。来源 pagination-v2。");
        }));
        assertEquals("COMPLETED", agent.ask("a", "我的项目适用什么分页规则？").status());
        assertEquals("COMPLETED", agent.ask("a", "我使用 v2").status());
        assertTrue(hasText(agent.history("a"), "请补充你使用的项目版本"));
        assertTrue(hasText(agent.history("a"), "我使用 v2"));
    }

    /** 验证模拟模型异常不泄漏异常消息，失败恢复原历史，随后清空移除历史。 */
    @Test void failureRestoresHistoryAndClearRemovesIt() {
        var count = new AtomicInteger();
        var agent = new ConversationAgent(model(p -> {
            if (count.incrementAndGet() > 1) throw new IllegalStateException("fake secret must not escape");
            return answer("第一轮完成");
        }));
        agent.ask("a", "第一轮");
        var before = agent.history("a");
        var result = agent.ask("a", "失败轮");
        assertEquals("STOPPED", result.status());
        assertFalse(result.toString().contains("fake secret"));
        assertEquals(before, agent.history("a"));
        assertEquals("CLEARED", agent.clear("a").status());
        assertTrue(agent.history("a").isEmpty());
    }

    /** 验证新建服务实例不会继承另一个实例的进程内历史，不验证持久化能力。 */
    @Test void newServiceHasNoPersistedHistory() {
        var original = new ConversationAgent(model(p -> answer("记下了")));
        original.ask("a", "上一进程的信息");
        var restarted = new ConversationAgent(model(p -> answer("新实例")));
        assertTrue(restarted.history("a").isEmpty());
    }

    /** 验证同批 9 个工具请求在执行前被整批拒绝，失败轮没有留下历史。 */
    @Test void toolBatchOverBudgetExecutesNothing() {
        var agent = new ConversationAgent(model(p -> calls("readDoc", "{\"docId\":\"pagination-v2\"}", 9)));
        var result = agent.ask("a", "读取");
        assertEquals("STOPPED", result.status());
        assertTrue(result.trace().stream().noneMatch(s -> s.startsWith("readDoc")));
        assertTrue(agent.history("a").isEmpty());
    }

    /** 验证未开放工具名在工具执行前被拒绝，并返回明确停止原因。 */
    @Test void unregisteredToolIsRejectedBeforeExecution() {
        var agent = new ConversationAgent(model(p -> calls("deleteFile", "{}", 1)));
        var result = agent.ask("a", "读取");
        assertEquals("STOPPED", result.status());
        assertTrue(result.answer().contains("未开放"));
    }

    /** 验证持续请求工具时最多调用模型 6 次，最后一轮不执行无法回填的第 6 次工具请求。 */
    @Test void repeatedToolRequestsStopAtSixModelCallsAndFiveExecutions() {
        var count = new AtomicInteger();
        var agent = new ConversationAgent(model(p -> {
            count.incrementAndGet();
            return calls("readDoc", "{\"docId\":\"pagination-v2\"}", 1);
        }));
        var result = agent.ask("a", "读取");
        assertEquals("STOPPED", result.status());
        assertEquals(6, count.get());
        assertEquals(5, result.trace().stream().filter(s -> s.startsWith("readDoc 返回：")).count());
    }

    /** 验证不存在文档的 NOT_FOUND 结果回填给模型，模型仍可据此结束本轮。 */
    @Test void missingDocumentIsFedBackToModel() {
        var count = new AtomicInteger();
        var agent = new ConversationAgent(model(p -> {
            if (count.incrementAndGet() == 1) return calls("readDoc", "{\"docId\":\"missing\"}", 1);
            var tool = p.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).findFirst().orElseThrow();
            assertTrue(tool.getResponses().getFirst().responseData().contains("NOT_FOUND"));
            return answer("没有该文档。");
        }));
        assertEquals("COMPLETED", agent.ask("a", "读取不存在的文档").status());
    }

    /** 验证空回复与注入时钟达到五分钟均停止本轮，并恢复为空的原会话历史。 */
    @Test void emptyAnswerAndExpiredBudgetAreStopped() {
        var empty = new ConversationAgent(model(p -> answer("")));
        assertEquals("STOPPED", empty.ask("a", "问题").status());
        assertTrue(empty.history("a").isEmpty());
        var clock = new AtomicLong();
        var expired = new ConversationAgent(model(p -> {
            clock.set(Duration.ofMinutes(5).toNanos()); return answer("过期结果");
        }), clock::get);
        assertEquals("STOPPED", expired.ask("a", "问题").status());
        assertTrue(expired.history("a").isEmpty());
    }

    /**
     * 阻塞一轮脚本模型调用，验证同会话提问与清空被拒绝，另一会话仍可完成。
     *
     * @throws Exception 测试线程等待、并发任务获取结果或模拟调用发生异常时传播
     */
    @Test void sameSessionConcurrentAskAndClearAreRejected() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var agent = new ConversationAgent(model(p -> {
            if (hasText(p.getInstructions(), "阻塞问题")) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
            }
            return answer("完成");
        }));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> agent.ask("a", "阻塞问题"));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals("STOPPED", agent.ask("a", "重叠问题").status());
                assertEquals("STOPPED", agent.clear("a").status());
                assertEquals("COMPLETED", agent.ask("b", "独立问题").status());
            } finally { release.countDown(); }
            assertEquals("COMPLETED", first.get(5, TimeUnit.SECONDS).status());
        }
    }

    /** 验证会话 ID 与问题输入约束，并确认登记第 33 个会话时停止而非调用模型。 */
    @Test void validatesInputsAndBoundsSessionCount() {
        var agent = new ConversationAgent(model(p -> answer("完成")));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("", "问题"));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("a", " "));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("a", "x".repeat(1001)));
        for (int i = 0; i < 32; i++) assertEquals("COMPLETED", agent.ask("s" + i, "问题").status());
        assertEquals("STOPPED", agent.ask("overflow", "问题").status());
    }
}
