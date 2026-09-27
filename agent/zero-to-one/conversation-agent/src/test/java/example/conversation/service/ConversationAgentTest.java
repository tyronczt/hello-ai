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
    static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
    static ChatResponse calls(String name, String arguments, int count) {
        var calls = IntStream.range(0, count).mapToObj(i ->
                new AssistantMessage.ToolCall("call-" + i, "function", name, arguments)).toList();
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(calls).build())));
    }
    static ChatModel model(Function<Prompt, ChatResponse> fn) {
        return new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) { return fn.apply(prompt); }
            // 与生产 OpenAiChatModel 相同的选项类型，确保工具回调能进入框架循环。
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return org.springframework.ai.openai.OpenAiChatOptions.builder().build();
            }
        };
    }
    static boolean hasText(List<Message> messages, String text) {
        return messages.stream().anyMatch(m -> m.getText() != null && m.getText().contains(text));
    }

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

    @Test void newServiceHasNoPersistedHistory() {
        var original = new ConversationAgent(model(p -> answer("记下了")));
        original.ask("a", "上一进程的信息");
        var restarted = new ConversationAgent(model(p -> answer("新实例")));
        assertTrue(restarted.history("a").isEmpty());
    }

    @Test void toolBatchOverBudgetExecutesNothing() {
        var agent = new ConversationAgent(model(p -> calls("readDoc", "{\"docId\":\"pagination-v2\"}", 9)));
        var result = agent.ask("a", "读取");
        assertEquals("STOPPED", result.status());
        assertTrue(result.trace().stream().noneMatch(s -> s.startsWith("readDoc")));
        assertTrue(agent.history("a").isEmpty());
    }

    @Test void unregisteredToolIsRejectedBeforeExecution() {
        var agent = new ConversationAgent(model(p -> calls("deleteFile", "{}", 1)));
        var result = agent.ask("a", "读取");
        assertEquals("STOPPED", result.status());
        assertTrue(result.answer().contains("未开放"));
    }

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

    @Test void validatesInputsAndBoundsSessionCount() {
        var agent = new ConversationAgent(model(p -> answer("完成")));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("", "问题"));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("a", " "));
        assertThrows(IllegalArgumentException.class, () -> agent.ask("a", "x".repeat(1001)));
        for (int i = 0; i < 32; i++) assertEquals("COMPLETED", agent.ask("s" + i, "问题").status());
        assertEquals("STOPPED", agent.ask("overflow", "问题").status());
    }
}
