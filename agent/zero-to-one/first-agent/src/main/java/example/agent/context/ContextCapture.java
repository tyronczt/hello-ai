package example.agent.context;

import example.agent.dto.AgentResultDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import tools.jackson.databind.json.JsonMapper;

/** 仅供显式教学运行使用的模型包装器；记录固定公开问题的上下文，不记录客户端或鉴权配置。 */
public final class ContextCapture implements ChatModel {
    /** 已有模型客户端，可能连接本机脚本服务，也可能是用户选择的真实服务。 */
    private final ChatModel delegate;
    /** 是否为离线脚本运行；离线用量一律标记为不可用。 */
    private final boolean offline;
    /** 顺序保存每次调用前的视图与调用后的观测结果。 */
    private final List<Map<String, Object>> rounds = new ArrayList<>();
    /** 项目已有的 JSON 实现，用于输出可读的教学记录。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 包装模型，不改变它的输入或执行策略。
     * @param delegate 本次教学使用的模型
     * @param offline 是否使用预设响应
     */
    public ContextCapture(ChatModel delegate, boolean offline) {
        this.delegate = delegate;
        this.offline = offline;
    }

    /**
     * 先核对完整工具配对，再记录调用前的实际 Spring AI 消息。
     * @param prompt 即将交给模型适配器的输入
     * @return 委托模型的原始响应
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        ContextSession.validateToolPairs(prompt.getInstructions());
        List<Map<String, Object>> messages = messagesOf(prompt.getInstructions());
        List<Map<String, Object>> tools = new ArrayList<>();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            for (var callback : options.getToolCallbacks()) {
                var definition = callback.getToolDefinition();
                tools.add(Map.of("name", definition.name(), "description", definition.description(),
                        "parameters", json.readTree(definition.inputSchema())));
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("round", rounds.size() + 1);
        row.put("view", "spring-ai-prompt; not the final HTTP request");
        row.put("messages", messages);
        row.put("tools", tools);
        row.put("messageFieldChars", ContextSession.textChars(prompt.getInstructions()));
        row.put("toolSchemaJsonChars", json.writeValueAsString(tools).length());
        row.put("systemSha256", sha256(prompt.getInstructions().getFirst().getText()));
        row.put("manifest", manifest(messages));
        rounds.add(row);
        long start = System.nanoTime();
        try {
            ChatResponse response = delegate.call(prompt);
            row.put("response", response == null || response.getResult() == null ? null
                    : messagesOf(List.of(response.getResult().getOutput())));
            var usage = response == null ? null : response.getMetadata().getUsage();
            if (!offline && usage != null && usage.getNativeUsage() != null) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("promptTokens", usage.getPromptTokens());
                values.put("completionTokens", usage.getCompletionTokens());
                values.put("totalTokens", usage.getTotalTokens());
                values.put("cacheReadInputTokensSdk", usage.getCacheReadInputTokens());
                values.put("cacheWriteInputTokensSdk", usage.getCacheWriteInputTokens());
                row.put("usage", values);
                row.put("usageSource", "provider response mapped by Spring AI; absent fields are unavailable");
            } else {
                row.put("usage", null);
                row.put("usageSource", offline ? "unavailable: scripted local response" : "unavailable: no native usage");
            }
            return response;
        } catch (RuntimeException ex) {
            row.put("errorType", ex.getClass().getName());
            throw ex;
        } finally {
            row.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** @return 本次教学的逐轮记录；仅供报告与离线检查读取 */
    public List<Map<String, Object>> rounds() {
        return List.copyOf(rounds);
    }

    /**
     * 为每次运行创建新目录，保留此前结果以便对照。
     * @param mode 当前模式
     * @param result Agent 实际返回的任务结果
     * @param wireRequests 本机脚本服务收到的 HTTP JSON；真实服务运行不提供此项
     * @return 本次报告所在目录
     * @throws IOException 输出目录或文件无法写入
     */
    public Path write(ContextMode mode, AgentResultDTO result, List<?> wireRequests) throws IOException {
        Path base = Path.of("target", "context-lab").toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path output = Files.createTempDirectory(base, (offline ? "offline-" : "live-")
                + mode.name().toLowerCase(java.util.Locale.ROOT) + "-");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("kind", offline ? "OFFLINE_SCRIPTED" : "LIVE_MODEL");
        report.put("mode", mode.name());
        report.put("question", ContextLab.QUESTION);
        report.put("status", result.status().name());
        report.put("answer", result.answer());
        report.put("trace", result.trace());
        report.put("rounds", rounds);
        Files.writeString(output.resolve("report.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        if (offline) {
            Files.writeString(output.resolve("wire-requests.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(wireRequests), StandardCharsets.UTF_8);
        }
        return output;
    }

    /**
     * 展开 Spring AI 的工具返回批次，生成便于核对的角色与调用字段视图。
     * @param messages 模型调用前的消息
     * @return 不含客户端选项、Header 或密钥的消息列表
     */
    public static List<Map<String, Object>> messagesOf(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage tool) {
                for (var response : tool.getResponses()) {
                    result.add(Map.of("role", "tool", "tool_call_id", response.id(),
                            "name", response.name(), "content", response.responseData()));
                }
            } else {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", message.getMessageType().getValue());
                item.put("content", message.getText());
                if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                    item.put("tool_calls", assistant.getToolCalls().stream().map(call ->
                            Map.of("id", call.id(), "type", call.type(), "function",
                                    Map.of("name", call.name(), "arguments", call.arguments()))).toList());
                }
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 标注消息的来源层级、变换与摘要，便于解释本轮选择；不是生产权限审计。
     * @param messages 展开后的消息视图
     * @return 与消息一一对应的教学清单
     */
    private List<Map<String, Object>> manifest(List<Map<String, Object>> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> message = messages.get(i);
            String content = String.valueOf(message.get("content"));
            String role = String.valueOf(message.get("role"));
            String source = role.equals("system") ? "application-rules"
                    : content.startsWith(ContextSession.STATE_MARKER) ? "runtime-state"
                    : content.startsWith(ContextSession.CHECKPOINT_MARKER) ? "completed-tool-batches"
                    : role.equals("tool") ? "tool:" + message.get("name")
                    : role.equals("assistant") ? "model-output"
                    : content.startsWith("[预加载") ? "packaged-skills" : "current-question";
            result.add(Map.of("messageIndex", i, "source", source, "scope", "current-task",
                    "transform", content.startsWith(ContextSession.CHECKPOINT_MARKER)
                            ? "deduplicated-verbatim-observations" : "verbatim",
                    "sha256", sha256(json.writeValueAsString(message))));
        }
        return result;
    }

    /**
     * 计算可重复核对的文本摘要；哈希相等不能证明缓存命中。
     * @param value 要摘要的文本
     * @return UTF-8 字节的 SHA-256 十六进制字符串
     */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 未提供 SHA-256。", ex);
        }
    }
}
