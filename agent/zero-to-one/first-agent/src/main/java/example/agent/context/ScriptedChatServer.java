package example.agent.context;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 仅监听本机随机端口的教学模型替身。真实执行 HTTP 适配与工具，但模型选择和最终回答是预设的。
 * 不接收外部连接、不转发网络请求、不返回伪造 Token 或缓存统计。
 */
public final class ScriptedChatServer implements AutoCloseable {
    /** 本次六轮对照使用的模式。 */
    private final ContextMode mode;
    /** JDK 自带的本地 HTTP 服务，无额外服务依赖。 */
    private final HttpServer server;
    /** 实际收到的请求 JSON，不保存 Header。 */
    private final List<JsonNode> requests = new ArrayList<>();
    /** 将服务器线程中的失败传回实验调用者。 */
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    /** 请求解析与脚本响应编码。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 启动只在本机可访问的随机端口。
     * @param mode 决定第一轮是否请求 Skill
     * @throws IOException 本机端口无法创建
     */
    public ScriptedChatServer(ContextMode mode) throws IOException {
        this.mode = mode;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    /** @return OpenAI 兼容客户端使用的本机基础地址 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** @return 实际 HTTP 请求的快照，用于与 Prompt 视图对照 */
    public synchronized List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    /** 检查本地服务处理期间是否发生错误，失败不能被脚本结果掩盖。 */
    public void verifyHealthy() {
        if (failure.get() != null) throw new IllegalStateException("本机协议检查失败。", failure.get());
    }

    /**
     * 接收有限大小的教学请求，并按轮次返回脚本。
     * @param exchange 当前本机 HTTP 交换
     * @throws IOException 响应无法写入
     */
    private synchronized void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                if (!exchange.getRequestMethod().equals("POST")) throw new IllegalArgumentException("只接受 POST。");
                byte[] bytes = exchange.getRequestBody().readNBytes(1_000_001);
                if (bytes.length > 1_000_000) throw new IllegalArgumentException("教学请求超出长度限制。");
                JsonNode request = json.readTree(bytes);
                verifyWirePairs(request.path("messages"));
                requests.add(request);
                int round = requests.size();
                if (round > 6) throw new IllegalStateException("脚本最多六轮。");
                List<Map<String, Object>> calls = calls(round);
                Map<String, Object> message = calls.isEmpty()
                        ? Map.of("role", "assistant", "content",
                            "[离线脚本预设回复] pageNo 从 1 开始，pageSize 最大为 100；默认值未规定。来源：pagination-v2。")
                        : Map.of("role", "assistant", "content", "", "tool_calls", calls);
                byte[] response = json.writeValueAsBytes(Map.of("id", "offline-" + round,
                        "object", "chat.completion", "created", 0, "model", "scripted-model",
                        "choices", List.of(Map.of("index", 0, "message", message,
                                "finish_reason", calls.isEmpty() ? "stop" : "tool_calls"))));
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (RuntimeException ex) {
                failure.compareAndSet(null, ex);
                byte[] error = "{\"error\":{\"message\":\"local fixture validation failed\",\"type\":\"invalid_request_error\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, error.length);
                exchange.getResponseBody().write(error);
            }
        }
    }

    /**
     * 为同一教学问题提供固定执行路径，包含一次失败与重复读取。
     * @param round 本机收到的模型调用轮次
     * @return 当前轮的工具调用；第六轮返回空列表表示预设最终回复
     */
    private List<Map<String, Object>> calls(int round) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (round == 1) {
            if (mode.usesSkills() && mode != ContextMode.EAGER) {
                result.add(call(round, 0, "readSkill", "{\"name\":\"doc-evidence\"}"));
            }
            result.add(call(round, 1, "searchDocs", "{\"keyword\":\"订单\"}"));
        } else if (round == 2) {
            result.add(call(round, 0, "readDoc", "{\"docId\":\"order-api-v2\"}"));
        } else if (round == 3) {
            result.add(call(round, 0, "readDoc", "{\"docId\":\"pagination-v2\"}"));
        } else if (round == 4) {
            result.add(call(round, 0, "readDoc", "{\"docId\":\"pagination-v2\"}"));
            result.add(call(round, 1, "readDoc", "{\"docId\":\"missing-doc\"}"));
        } else if (round == 5) {
            result.add(call(round, 0, "searchDocs", "{\"keyword\":\"订单\"}"));
            result.add(call(round, 1, "readDoc", "{\"docId\":\"pagination-v2\"}"));
        }
        return result;
    }

    /**
     * 创建一个 Chat Completions 工具请求。
     * @param round 当前轮次
     * @param index 批次中的位置
     * @param name 注册工具名
     * @param arguments JSON 参数字符串
     * @return 协议中的工具调用对象
     */
    private static Map<String, Object> call(int round, int index, String name, String arguments) {
        return Map.of("id", "offline-call-round-" + round + "-item-" + index, "type", "function",
                "function", Map.of("name", name, "arguments", arguments));
    }

    /**
     * 独立核对真正收到的 HTTP 消息，避免只检查 Java 对象而遗漏适配问题。
     * @param messages 请求中的 messages 数组
     */
    public static void verifyWirePairs(JsonNode messages) {
        java.util.Set<String> pending = new java.util.LinkedHashSet<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (JsonNode message : messages) {
            String role = message.path("role").asString("");
            if (role.equals("tool")) {
                if (!pending.remove(message.path("tool_call_id").asString(""))) {
                    throw new IllegalArgumentException("HTTP 消息中出现孤立工具结果。");
                }
            } else {
                if (!pending.isEmpty()) throw new IllegalArgumentException("HTTP 工具批次被其他消息打断。");
                if (role.equals("assistant")) {
                    for (JsonNode call : message.path("tool_calls")) {
                        String id = call.path("id").asString("");
                        if (id.isBlank() || !seen.add(id)) throw new IllegalArgumentException("HTTP 调用 ID 无效。");
                        pending.add(id);
                    }
                }
            }
        }
        if (!pending.isEmpty()) throw new IllegalArgumentException("HTTP 工具返回缺失。");
    }

    /** 停止本机教学服务，释放随机端口。 */
    @Override
    public void close() {
        server.stop(0);
    }
}
