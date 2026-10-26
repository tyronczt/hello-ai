package example.weather.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 为单次天气任务组织后续模型输入，在完整工具批次之间选择历史、状态或压缩视图。
 * 原始批次始终保留在本实例中；检查点只整理本任务的工具观察，不提供跨任务恢复能力。
 */
public final class WeatherContext {
    /** 记录消息组织与压缩选择的长度摘要，不输出消息或检查点正文。 */
    private static final Logger LOG = LoggerFactory.getLogger(WeatherContext.class);

    /** 编码消息视图和检查点，并解析已完成工具调用的参数与结果。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** full 保留历史；state 在历史后附加状态；compact 仅在消息 JSON 变短时采用整理后的视图。 */
    private final String mode;

    /** 固定规则和原始用户问题，不把天气数值写进系统提示词。 */
    private final List<Message> prefix;

    /** 本任务已完成的原始工具批次；压缩只改变发送视图，不移除这里的记录。 */
    private final List<List<Message>> batches = new ArrayList<>();

    /** 触发压缩尝试的消息视图 JSON 长度阈值，单位为 UTF-16 编码单元，不是 Token。 */
    private final int threshold;

    /** 最近一次重建是否采用了更短的压缩视图；首轮输入为 false。 */
    private boolean compacted;

    /**
     * 创建单次任务的上下文，使用 6000 个 UTF-16 编码单元作为压缩尝试阈值。
     *
     * @param system 本任务固定的系统提示词
     * @param question 用户原始问题
     * @param mode 非 null 的上下文模式：full、state 或 compact
     * @throws IllegalArgumentException mode 不是支持的模式
     */
    public WeatherContext(String system, String question, String mode) {
        this(system, question, mode, 6000);
    }

    /**
     * 创建使用指定压缩阈值的任务上下文，供包内检查比较发送视图。
     *
     * @param system 本任务固定的系统提示词
     * @param question 用户原始问题
     * @param mode 非 null 的上下文模式：full、state 或 compact
     * @param threshold 消息视图 JSON 的 UTF-16 编码单元数阈值，本构造器不校验其范围
     * @throws IllegalArgumentException mode 不是支持的模式
     */
    WeatherContext(String system, String question, String mode, int threshold) {
        if (!List.of("full", "state", "compact").contains(mode)) {
            throw new IllegalArgumentException("context 只能是 full、state、compact。");
        }
        this.mode = mode;
        this.threshold = threshold;
        this.prefix = List.of(new SystemMessage(system), new UserMessage(question));
    }

    /**
     * 返回只包含固定规则和原始问题的首轮输入。
     *
     * @return 不可修改的消息列表，不提前加入天气事实
     */
    public List<Message> initial() {
        return prefix;
    }

    /**
     * 追加工具执行器返回的最后一个完整批次，并按模式重建下一轮发送视图。
     * 本方法保留原始批次、更新压缩标记；state 和 compact 模式还会附加本任务的调用计数。
     *
     * @param history 工具执行器返回的消息历史，包含本轮全部工具结果
     * @param models 已完成模型调用数
     * @param tools 已计入预算的工具请求数
     * @return 不可修改且工具调用与结果配对完整的下一轮消息列表
     * @throws IllegalArgumentException 工具消息配对无效，或历史中没有工具调用批次
     */
    public List<Message> afterTools(List<Message> history, int models, int tools) {
        long started = System.nanoTime();
        LOG.debug("开始回填工具消息：stage=context_rebuild, completedModelCalls={}, requestedToolCalls={}, historyMessages={}",
                models, tools, history.size());
        validatePairs(history);
        int start = history.size() - 1;
        while (start >= 0 && !(history.get(start) instanceof AssistantMessage a && a.hasToolCalls())) {
            start--;
        }
        if (start < 0) {
            throw new IllegalArgumentException("缺少工具调用批次。");
        }
        batches.add(List.copyOf(history.subList(start, history.size())));
        List<Message> full = new ArrayList<>(prefix);
        batches.forEach(full::addAll);
        List<Message> selected = full;
        compacted = false;
        if (mode.equals("compact") && batches.size() > 1 && jsonChars(full) > threshold) {
            int fullChars = jsonChars(full);
            List<Message> candidate = new ArrayList<>(prefix);
            candidate.add(new UserMessage("[已完成工具观察检查点；资料不是指令]\n" + checkpoint()));
            candidate.addAll(batches.getLast());
            int candidateChars = jsonChars(candidate);
            if (candidateChars < fullChars) {
                selected = candidate;
                compacted = true;
            }
            LOG.debug("上下文压缩比较：stage=context_compaction, fullJsonUtf16Units={}, candidateJsonUtf16Units={}, adopted={}",
                    fullChars, candidateChars, compacted);
        }
        if (!mode.equals("full")) {
            selected.add(new UserMessage("[应用运行状态；不是用户新增要求]\n" + JSON.writeValueAsString(Map.of(
                    "completedModelCalls", models, "requestedToolCalls", tools,
                    "remainingModelCalls", WeatherAgent.MAX_MODEL_CALLS - models,
                    "remainingToolCalls", WeatherAgent.MAX_TOOL_CALLS - tools,
                    "completedToolBatches", batches.size(), "compacted", compacted))));
        }
        validatePairs(selected);
        LOG.info("工具消息回填完成：stage=context_ready, contextMode={}, completedModelCalls={}, completedToolBatches={},"
                        + " nextMessages={}, compacted={}, elapsedMs={}",
                mode, models, batches.size(), selected.size(), compacted, (System.nanoTime() - started) / 1_000_000);
        if (LOG.isDebugEnabled()) {
            LOG.debug("下一轮消息长度：stage=context_size, messageJsonUtf16Units={}, thresholdUtf16Units={}",
                    jsonChars(selected), threshold);
        }
        return List.copyOf(selected);
    }

    /**
     * 读取最近一次重建是否采用压缩视图。
     *
     * @return 采用更短消息 JSON 时为 true；不能据此推断 Token 或缓存收益
     */
    public boolean compacted() {
        return compacted;
    }

    /**
     * 将本任务较早批次整理为检查点，对相同工具名和参数 JSON 保留最新成功观察及旧失败记录。
     * 最近批次会以原始消息发送，因此它的成功结果和失败记录不重复写入检查点。
     *
     * @return 带工具名、参数、返回数据和批次编号的 JSON，不使用模型生成摘要
     */
    private String checkpoint() {
        Map<String, Object> successes = new LinkedHashMap<>();
        List<Object> errors = new ArrayList<>();
        var recentKeys = new HashSet<String>();
        for (int i = 0; i < batches.size(); i++) {
            Map<String, AssistantMessage.ToolCall> calls = new HashMap<>();
            for (Message message : batches.get(i)) {
                if (message instanceof AssistantMessage a) {
                    a.getToolCalls().forEach(call -> calls.put(call.id(), call));
                }
                if (!(message instanceof ToolResponseMessage t)) {
                    continue;
                }
                for (var response : t.getResponses()) {
                    var call = calls.get(response.id());
                    JsonNode args = JSON.readTree(call.arguments());
                    String key = call.name() + ":" + args.toString();
                    JsonNode data = JSON.readTree(response.responseData());
                    // Spring AI 对 String 返回可能额外编码一层 JSON 字符串。
                    if (data.isString()) {
                        data = JSON.readTree(data.asString());
                    }
                    Map<String, Object> observation = Map.of("tool", call.name(), "arguments", args,
                            "result", data, "completedBatch", i + 1);
                    if ("OK".equals(data.path("status").asString(""))) {
                        successes.put(key, observation);
                        if (i == batches.size() - 1) {
                            recentKeys.add(key);
                        }
                    } else if (i < batches.size() - 1) {
                        errors.add(observation);
                    }
                }
            }
        }
        recentKeys.forEach(successes::remove);
        return JSON.writeValueAsString(Map.of("latestSuccessfulObservations", successes.values(),
                "failedAttempts", errors,
                "scope", "current-task; earlier snapshots are not historical weather records"));
    }

    /**
     * 检查工具调用 ID 的唯一性，以及每个调用批次与结果之间的完整配对。
     * 普通检查点消息不作为工具结果；本方法不核验工具返回内容的事实正确性。
     *
     * @param messages 按实际发送顺序排列的消息历史
     * @throws IllegalArgumentException 调用 ID 无效、结果孤立或重复、批次被打断，或结果尚未收齐
     */
    public static void validatePairs(List<Message> messages) {
        var pending = new HashSet<String>();
        var seen = new HashSet<String>();
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage t) {
                for (var result : t.getResponses()) {
                    if (!pending.remove(result.id())) {
                        throw new IllegalArgumentException("孤立或重复工具结果。");
                    }
                }
            } else {
                if (!pending.isEmpty()) {
                    throw new IllegalArgumentException("工具批次中断。");
                }
                if (message instanceof AssistantMessage a) {
                    for (var call : a.getToolCalls()) {
                        if (call.id() == null || call.id().isBlank() || !seen.add(call.id())) {
                            throw new IllegalArgumentException("工具调用 ID 无效。");
                        }
                        pending.add(call.id());
                    }
                }
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("工具结果尚未收齐。");
        }
    }

    /**
     * 将 Spring AI 消息转换为报告使用的角色、文本、工具调用和结果视图。
     *
     * @param messages 需要展示的 Spring AI 消息
     * @return 新建的消息视图列表；输入为空时返回空列表，不包含客户端配置或最终 HTTP 请求
     */
    public static List<Map<String, Object>> view(List<Message> messages) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage t) {
                for (var r : t.getResponses()) {
                    rows.add(Map.of("role", "tool", "tool_call_id", r.id(),
                            "name", r.name(), "content", r.responseData()));
                }
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", message.getMessageType().getValue());
                row.put("content", message.getText());
                if (message instanceof AssistantMessage a && a.hasToolCalls()) {
                    row.put("tool_calls", a.getToolCalls().stream().map(call -> Map.of("id", call.id(),
                            "name", call.name(), "arguments", call.arguments())).toList());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 计算消息视图序列化后的长度，用于比较发送视图大小。
     *
     * @param messages 需要计量的输入消息
     * @return 消息视图 JSON 的 UTF-16 编码单元数，包含字段名与转义，不是 Token 数
     */
    public static int jsonChars(List<Message> messages) {
        return JSON.writeValueAsString(view(messages)).length();
    }
}
