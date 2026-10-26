package example.weather.agent;

import example.weather.tool.WeatherTools;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

/**
 * 天气任务的模型与工具循环，负责整批预检、调用预算和运行记录。
 * 每次 run 独立维护工具、消息与计数；模型提出动作，应用执行后将结果交回下一轮。
 */
public final class WeatherAgent {
    /** 记录轮次、预算、阶段和结果摘要，不记录提示词、模型正文或原始工具参数。 */
    private static final Logger LOG = LoggerFactory.getLogger(WeatherAgent.class);

    /** 包含最终回答在内的模型调用上限。 */
    public static final int MAX_MODEL_CALLS = 8;

    /** 已通过整批预检的工具请求上限；执行后失败也计数，预检拒绝的批次不计数。 */
    public static final int MAX_TOOL_CALLS = 12;

    /** 阶段之间检查的任务时长上限，不能中断已经阻塞的外部调用。 */
    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(5);

    /** 注入的模型客户端；每次 run 创建独立工具、历史与计数。 */
    private final ChatModel model;

    /** 执行工具并生成与调用 ID 对应的结果消息。 */
    private final ToolCallingManager manager = ToolCallingManager.builder().build();

    /** 用于工具参数预检与报告编码。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 接收由应用配置创建的模型客户端，构造阶段不发起模型请求。
     *
     * @param model 本任务使用的非空模型客户端
     */
    public WeatherAgent(ChatModel model) {
        this.model = model;
    }

    /**
     * 创建独立天气工具并执行任务，不复用其他任务的地点或消息历史。
     *
     * @param question 本次用户问题，非空白且不超过 1000 个 UTF-16 单元
     * @param promptMode 提示词模式：basic、rules 或 examples
     * @param contextMode 消息组织模式：full、state 或 compact
     * @return 实际执行报告；COMPLETED 表示非空候选回答，STOPPED 表示未完成或处理失败
     * @throws IllegalArgumentException 问题或模式不符合要求
     */
    public Map<String, Object> run(String question, String promptMode, String contextMode) {
        return run(question, promptMode, contextMode, new WeatherTools());
    }

    /**
     * 使用指定工具执行一次任务，包内检查可注入天气替身。
     * 模型或工具循环中的运行时异常转为 STOPPED 报告；输入及模式校验在循环前完成。
     *
     * @param question 本次用户问题，非空白且不超过 1000 个 UTF-16 单元
     * @param promptMode 提示词模式：basic、rules 或 examples
     * @param contextMode 消息组织模式：full、state 或 compact
     * @param tools 仅供本次任务使用的工具实例，不与其他任务共享状态
     * @return 包含逐轮消息、工具事件、停止状态及候选回答的报告
     * @throws IllegalArgumentException 问题或模式不符合要求
     */
    Map<String, Object> run(String question, String promptMode, String contextMode, WeatherTools tools) {
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new IllegalArgumentException("问题必须为 1～1000 个字符。");
        }
        var context = new WeatherContext(WeatherPrompts.of(promptMode), question, contextMode);
        var callbacks = ToolCallbacks.from(tools);
        // 2.0.1 的新 Builder 自带默认模型；从已配置选项派生，避免覆盖 YAML 中的模型与参数。
        var configured = model.getOptions();
        var builder = configured instanceof OpenAiChatOptions openAi ? openAi.mutate() : OpenAiChatOptions.builder();
        var options = builder.toolCallbacks(callbacks).build();
        Prompt prompt = new Prompt(context.initial(), options);
        List<Map<String, Object>> rounds = new ArrayList<>();
        Map<String, Object> report = createReport(question, promptMode, contextMode, callbacks, rounds);
        Set<String> seenCalls = new HashSet<>();
        int toolCalls = 0;
        long started = System.nanoTime();
        int activeRound = 0;
        String phase = "agent_start";
        String stopReason = "MODEL_CALL_LIMIT";
        long phaseStarted = started;
        LOG.info("Agent 开始：stage=agent_start, promptMode={}, contextMode={}, maxModelCalls={}, maxToolCalls={}",
                promptMode, contextMode, MAX_MODEL_CALLS, MAX_TOOL_CALLS);
        try {
            for (int round = 1; round <= MAX_MODEL_CALLS; round++) {
                activeRound = round;
                if (expired(started)) {
                    stopReason = "TASK_TIMEOUT_BEFORE_MODEL";
                    report.put("answer", "任务超出五分钟预算。");
                    break;
                }
                phase = "model_prepare";
                phaseStarted = System.nanoTime();
                WeatherContext.validatePairs(prompt.getInstructions());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("round", round);
                row.put("messages", WeatherContext.view(prompt.getInstructions()));
                row.put("messageJsonUtf16Units", WeatherContext.jsonChars(prompt.getInstructions()));
                row.put("compacted", context.compacted());
                rounds.add(row);
                LOG.info("模型调用开始：stage=model_call, round={}, messages={}, messageJsonUtf16Units={},"
                                + " compacted={}, remainingModelCalls={}, remainingToolCalls={}",
                        round, prompt.getInstructions().size(), row.get("messageJsonUtf16Units"),
                        context.compacted(), MAX_MODEL_CALLS - round + 1, MAX_TOOL_CALLS - toolCalls);
                phase = "model_call";
                long before = System.nanoTime();
                phaseStarted = before;
                var response = model.call(prompt);
                row.put("elapsedMs", (System.nanoTime() - before) / 1_000_000);
                LOG.info("模型调用返回：stage=model_return, round={}, elapsedMs={}", round, row.get("elapsedMs"));
                if (expired(started)) {
                    stopReason = "TASK_TIMEOUT_AFTER_MODEL";
                    report.put("answer", "模型返回时已超出任务预算。");
                    break;
                }
                phase = "model_response";
                phaseStarted = System.nanoTime();
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    stopReason = "INVALID_MODEL_RESPONSE";
                    report.put("answer", "模型没有返回有效结果。");
                    break;
                }
                var output = response.getResult().getOutput();
                recordResponse(row, response);
                LOG.info("模型响应摘要：stage=model_response, round={}, toolCallCount={}, answerUtf16Units={}",
                        round, response.hasToolCalls() ? output.getToolCalls().size() : 0,
                        output.getText() == null ? 0 : output.getText().length());
                LOG.debug("模型原生用量：stage=model_usage, round={}, usage={}", round, row.get("usage"));
                if (!response.hasToolCalls()) {
                    if (output.getText() == null || output.getText().isBlank()) {
                        stopReason = "EMPTY_ANSWER";
                        report.put("answer", "模型返回空答案。");
                    } else {
                        stopReason = "CANDIDATE_READY";
                        report.put("status", "COMPLETED");
                        report.put("answer", output.getText());
                    }
                    break;
                }
                var calls = output.getToolCalls();
                if (round == MAX_MODEL_CALLS || calls.size() > MAX_TOOL_CALLS - toolCalls) {
                    stopReason = "INSUFFICIENT_TOOL_BATCH_BUDGET";
                    report.put("answer", "剩余预算不足以执行完整工具批次并读取结果。");
                    break;
                }
                phase = "tool_precheck";
                phaseStarted = System.nanoTime();
                validateCalls(calls, seenCalls);
                LOG.info("工具批次预检通过：stage=tool_precheck, round={}, batchSize={}, tools={}",
                        round, calls.size(), calls.stream().map(AssistantMessage.ToolCall::name).toList());
                toolCalls += calls.size();
                phase = "tool_execute";
                phaseStarted = System.nanoTime();
                LOG.info("工具批次开始：stage=tool_batch_start, round={}, requestedToolCalls={}", round, toolCalls);
                var history = manager.executeToolCalls(prompt, response).conversationHistory();
                LOG.info("工具批次结束：stage=tool_batch_end, round={}, elapsedMs={}",
                        round, (System.nanoTime() - phaseStarted) / 1_000_000);
                phase = "context_rebuild";
                phaseStarted = System.nanoTime();
                prompt = new Prompt(context.afterTools(history, round, toolCalls), options);
            }
        } catch (RuntimeException ex) {
            stopReason = "EXECUTION_FAILURE";
            LOG.error("Agent 执行异常：stage=agent_failed, phase={}, round={}, exceptionType={}, phaseElapsedMs={}",
                    phase, activeRound, ex.getClass().getName(), (System.nanoTime() - phaseStarted) / 1_000_000);
            LOG.debug("Agent 异常代码位置：stage=agent_failure_frames, frames={}", Arrays.toString(ex.getStackTrace()));
            report.put("answer", "模型、工具或协议处理失败；请检查报告中的 errorType 和工具结果。");
            report.put("errorType", ex.getClass().getSimpleName());
        }
        report.put("toolEvents", tools.events());
        report.put("requestedToolCalls", toolCalls);
        report.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        report.put("completionMeaning", "候选回答已产生；COMPLETED 不证明天气事实或建议已自动核验。");
        LOG.atLevel("COMPLETED".equals(report.get("status")) ? Level.INFO : Level.WARN).log(
                "Agent 结束：stage=agent_end, status={}, reason={}, modelCalls={}, requestedToolCalls={}, elapsedMs={}",
                report.get("status"), stopReason, rounds.size(), toolCalls, report.get("elapsedMs"));
        return report;
    }

    /**
     * 初始化报告元数据和工具定义，逐轮记录列表由执行循环继续填充。
     *
     * @param question 原始用户问题
     * @param promptMode 已校验的提示词模式
     * @param contextMode 已校验的消息组织模式
     * @param callbacks 本次已注册的工具回调
     * @param rounds 本次任务的可变逐轮记录列表，与报告共享同一个列表
     * @return 默认状态为 STOPPED 的可变报告
     */
    private Map<String, Object> createReport(String question, String promptMode, String contextMode,
            ToolCallback[] callbacks, List<Map<String, Object>> rounds) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("question", question);
        report.put("promptMode", promptMode);
        report.put("contextMode", contextMode);
        report.put("view", "Spring AI Prompt, not final HTTP payload");
        report.put("tools", Arrays.stream(callbacks).map(callback -> Map.of(
                "name", callback.getToolDefinition().name(),
                "description", callback.getToolDefinition().description(),
                "schema", json.readTree(callback.getToolDefinition().inputSchema()))).toList());
        report.put("rounds", rounds);
        report.put("status", "STOPPED");
        report.put("answer", "调用预算耗尽。");
        return report;
    }

    /**
     * 将模型输出和原生用量写入本轮记录，不用文本长度估算缺失用量。
     *
     * @param row 当前轮次的可变报告记录
     * @param response 已确认包含有效输出的模型响应
     */
    private void recordResponse(Map<String, Object> row, ChatResponse response) {
        row.put("response", WeatherContext.view(List.of(response.getResult().getOutput())));
        var usage = response.getMetadata().getUsage();
        Map<String, Object> usageFields = null;
        if (usage != null && usage.getNativeUsage() != null) {
            usageFields = new LinkedHashMap<>();
            usageFields.put("promptTokens", usage.getPromptTokens());
            usageFields.put("completionTokens", usage.getCompletionTokens());
            usageFields.put("totalTokens", usage.getTotalTokens());
        }
        row.put("usage", usageFields);
    }

    /**
     * 在阶段边界检查任务时长，不取消已经发出的模型或工具请求。
     *
     * @param started 任务开始时的 System.nanoTime 单调时钟读数，单位纳秒
     * @return 已耗时是否达到五分钟预算
     */
    private boolean expired(long started) {
        return System.nanoTime() - started >= TASK_TIMEOUT.toNanos();
    }

    /**
     * 整批验证名称、参数和调用 ID；任何一项失败都不执行该批次。
     * 验证通过后登记整批 ID；地点是否已检索仍由天气工具检查，不在这里判断。
     *
     * @param calls 模型提出的调用
     * @param seen 已通过预检的调用 ID 集合；本方法追加新 ID，不阻止新 ID 下的重复参数请求
     * @throws IllegalArgumentException ID 为空或重复、工具未开放、参数结构或类型不符合要求
     */
    private void validateCalls(List<AssistantMessage.ToolCall> calls, Set<String> seen) {
        var batchIds = new HashSet<String>();
        for (var call : calls) {
            if (call.id() == null || call.id().isBlank() || seen.contains(call.id()) || !batchIds.add(call.id())) {
                throw new IllegalArgumentException("工具调用 ID 重复或为空。");
            }
            var args = json.readTree(call.arguments());
            if (args == null || !args.isObject() || args.size() != 1) {
                throw new IllegalArgumentException("工具参数结构无效。");
            }
            boolean valid = switch (call.name()) {
                case "searchLocations" -> args.path("city").isString();
                case "getWeather" -> args.path("locationId").isIntegralNumber();
                default -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException("未开放工具或参数类型无效。");
            }
        }
        seen.addAll(batchIds);
    }
}
