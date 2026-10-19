package example.weather;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;

/** 天气教学循环；模型提议动作，应用预检、执行并组织下一轮上下文。 */
public final class WeatherAgent {
    /** 包含最终回答在内的模型调用上限。 */
    public static final int MAX_MODEL_CALLS = 8;
    /** 失败工具请求也计入的任务工具预算。 */
    public static final int MAX_TOOL_CALLS = 12;
    /** 注入的模型客户端；每次 run 创建独立工具、历史与计数。 */
    private final ChatModel model;
    /** 执行工具并生成与调用 ID 对应的结果消息。 */
    private final ToolCallingManager manager = ToolCallingManager.builder().build();
    /** 用于工具参数预检与报告编码。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /** @param model 已由配置创建的客户端，不在构造器中调用模型 */
    public WeatherAgent(ChatModel model) {
        this.model = model;
    }

    /** @param question 本次问题 @param promptMode 提示词模式 @param contextMode 上下文模式 @return 实际执行报告 */
    public Map<String, Object> run(String question, String promptMode, String contextMode) {
        return run(question, promptMode, contextMode, new WeatherTools());
    }

    /** @param question 问题 @param promptMode 提示词模式 @param contextMode 上下文模式 @param tools 本任务工具 @return 报告 */
    Map<String, Object> run(String question, String promptMode, String contextMode, WeatherTools tools) {
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new IllegalArgumentException("问题必须为 1～1000 个字符。");
        }
        var context = new WeatherContext(WeatherPrompts.of(promptMode), question, contextMode);
        var callbacks = ToolCallbacks.from(tools);
        // 2.0.1 的新 Builder 自带默认模型；从已配置选项派生，避免覆盖 YAML 中的模型与参数。
        var configured = model.getDefaultOptions();
        var builder = configured instanceof OpenAiChatOptions openAi ? openAi.mutate() : OpenAiChatOptions.builder();
        var options = builder.toolCallbacks(callbacks).build();
        Prompt prompt = new Prompt(context.initial(), options);
        List<Map<String, Object>> rounds = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("question", question);
        report.put("promptMode", promptMode);
        report.put("contextMode", contextMode);
        report.put("view", "Spring AI Prompt, not final HTTP payload");
        report.put("tools", java.util.Arrays.stream(callbacks).map(c -> Map.of("name", c.getToolDefinition().name(),
                "description", c.getToolDefinition().description(), "schema", json.readTree(c.getToolDefinition().inputSchema()))).toList());
        report.put("rounds", rounds);
        report.put("status", "STOPPED");
        report.put("answer", "调用预算耗尽。");
        Set<String> seenCalls = new HashSet<>();
        int toolCalls = 0;
        long started = System.nanoTime();
        try {
            for (int round = 1; round <= MAX_MODEL_CALLS; round++) {
                if (expired(started)) { report.put("answer", "任务超出五分钟预算。"); break; }
                WeatherContext.validatePairs(prompt.getInstructions());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("round", round);
                row.put("messages", WeatherContext.view(prompt.getInstructions()));
                row.put("messageJsonUtf16Units", WeatherContext.jsonChars(prompt.getInstructions()));
                row.put("compacted", context.compacted());
                rounds.add(row);
                long before = System.nanoTime();
                var response = model.call(prompt);
                row.put("elapsedMs", (System.nanoTime() - before) / 1_000_000);
                if (expired(started)) { report.put("answer", "模型返回时已超出任务预算。"); break; }
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    report.put("answer", "模型没有返回有效结果。"); break;
                }
                var output = response.getResult().getOutput();
                row.put("response", WeatherContext.view(List.of(output)));
                var usage = response.getMetadata().getUsage();
                Map<String, Object> usageFields = null;
                if (usage != null && usage.getNativeUsage() != null) {
                    usageFields = new LinkedHashMap<>();
                    usageFields.put("promptTokens", usage.getPromptTokens());
                    usageFields.put("completionTokens", usage.getCompletionTokens());
                    usageFields.put("totalTokens", usage.getTotalTokens());
                }
                row.put("usage", usageFields);
                if (!response.hasToolCalls()) {
                    if (output.getText() == null || output.getText().isBlank()) {
                        report.put("answer", "模型返回空答案。");
                    } else {
                        report.put("status", "COMPLETED");
                        report.put("answer", output.getText());
                    }
                    break;
                }
                var calls = output.getToolCalls();
                if (round == MAX_MODEL_CALLS || calls.size() > MAX_TOOL_CALLS - toolCalls) {
                    report.put("answer", "剩余预算不足以执行完整工具批次并读取结果。"); break;
                }
                validateCalls(calls, seenCalls);
                toolCalls += calls.size();
                var history = manager.executeToolCalls(prompt, response).conversationHistory();
                prompt = new Prompt(context.afterTools(history, round, toolCalls), options);
            }
        } catch (RuntimeException ex) {
            report.put("answer", "模型、工具或协议处理失败；请检查报告中的 errorType 和工具结果。");
            report.put("errorType", ex.getClass().getSimpleName());
        }
        report.put("toolEvents", tools.events());
        report.put("requestedToolCalls", toolCalls);
        report.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        report.put("completionMeaning", "候选回答已产生；COMPLETED 不证明天气事实或建议已自动核验。");
        return report;
    }

    /** @param started 开始时单调时钟读数 @return 是否超出阶段之间检查的五分钟预算 */
    private boolean expired(long started) {
        return System.nanoTime() - started >= Duration.ofMinutes(5).toNanos();
    }

    /**
     * 整批验证名称、参数和调用 ID；任何一项失败都不执行该批次。
     * @param calls 模型提出的调用
     * @param seen 之前执行过的调用 ID，防止压缩历史后重复使用
     */
    private void validateCalls(List<AssistantMessage.ToolCall> calls, Set<String> seen) {
        var batchIds = new HashSet<String>();
        for (var call : calls) {
            if (call.id() == null || call.id().isBlank() || seen.contains(call.id()) || !batchIds.add(call.id())) {
                throw new IllegalArgumentException("工具调用 ID 重复或为空。");
            }
            var args = json.readTree(call.arguments());
            if (args == null || !args.isObject() || args.size() != 1) throw new IllegalArgumentException("工具参数结构无效。");
            boolean valid = switch (call.name()) {
                case "searchLocations" -> args.path("city").isString();
                case "getWeather" -> args.path("locationId").isIntegralNumber();
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("未开放工具或参数类型无效。");
        }
        seen.addAll(batchIds);
    }
}
