package example.weather.lab;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** 本机 HTTP 协议检查；使用脚本化模型响应，不访问 DeepSeek、不测量模型质量。 */
public final class DeepSeekPromptLabProtocolCheck {
    /** 累计实际执行的断言数，仅用于本次独立检查进程。 */
    private static int assertions;

    /** 检查入口不创建实例。 */
    private DeepSeekPromptLabProtocolCheck() {
    }

    /**
     * 检查请求/回填协议、夹具字段核验、预算、输入边界、远端错误和凭据隔离。
     *
     * @param args 不使用命令行参数
     * @throws IOException 本机替身服务无法启动或关闭
     */
    public static void main(String[] args) throws IOException {
        String unknown = answer("ANSWER", "DEMO-002", "接口请求超时", "部分请求失败", null);
        try (Stub stub = new Stub(List.of(tool("c1", "DEMO-002"), completion(unknown, "stop")), 200)) {
            var report = stub.client().run("tools", "rules", "unknown");
            check(Boolean.TRUE.equals(report.get("passed")), "未知恢复时间通过有限字段核验");
            check(stub.requests.size() == 2, "一次工具往返需要两次模型请求");
            JsonNode first = stub.requests.getFirst();
            JsonNode second = stub.requests.getLast();
            check(first.path("messages").size() == 2 && second.path("messages").size() == 4,
                    "消息从 system/user 增至完整 assistant/tool 对");
            check("auto".equals(first.path("tool_choice").asString()), "不强制模型一定调用工具");
            check("disabled".equals(first.path("thinking").path("type").asString()), "显式关闭思考模式");
            check(second.path("messages").get(2).equals(DeepSeekPromptLab.JSON.readTree(tool("c1", "DEMO-002"))
                    .path("choices").get(0).path("message")), "assistant 消息原样回填");
            check("c1".equals(second.path("messages").get(3).path("tool_call_id").asString()), "工具结果 ID 配对");
            check(DeepSeekPromptLab.JSON.readTree(second.path("messages").get(3).path("content").asString())
                    .path("recoveryTime").isNull(), "工具中 null 不改写成零或字符串");
            check(!DeepSeekPromptLab.JSON.writeValueAsString(report).contains(Stub.KEY), "报告不包含请求凭据");
            JsonNode view = DeepSeekPromptLab.JSON.valueToTree(report);
            check(view.path("rounds").get(0).path("response").path("usage").path("prompt_tokens").asInt() == 10,
                    "保留替身返回的 usage，不从字数推算");
            check(view.path("rounds").get(0).path("elapsedMs").asLong() >= 0, "保留每轮完整请求耗时");
        }
        for (String caseName : List.of("complete", "not-found", "failure")) {
            String id = switch (caseName) {
                case "complete" -> "DEMO-001";
                case "not-found" -> "DEMO-404";
                default -> "DEMO-503";
            };
            String expected = "complete".equals(caseName)
                    ? answer("ANSWER", id, "接口请求超时", "部分请求失败", "10:20")
                    : answer("not-found".equals(caseName) ? "NOT_FOUND" : "UNAVAILABLE", id, null, null, null);
            try (Stub stub = new Stub(List.of(tool("c1", id), completion(expected, "stop")), 200)) {
                check(Boolean.TRUE.equals(stub.client().run("tools", "examples", caseName).get("passed")),
                        "夹具与字段契约：" + caseName);
            }
        }
        try (Stub stub = new Stub(List.of(completion(answer("NEED_ID", null, null, null, null), "stop")), 200)) {
            var report = stub.client().run("tools", "rules", "missing-id");
            check(Boolean.TRUE.equals(report.get("passed")), "缺编号可以不调用工具");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "缺编号任务没有执行工具");
        }
        try (Stub stub = new Stub(List.of(completion(unknown, "stop")), 200)) {
            var report = stub.client().run("tools", "rules", "unknown");
            check(!Boolean.TRUE.equals(report.get("passed")), "猜中字段但没有工具依据仍失败");
            check(((List<?>) report.get("checks")).contains("TOOL_EVIDENCE"), "行为失败有独立错误码");
        }
        try (Stub stub = new Stub(List.of(tool("c1", "DEMO-002"), tool("c2", "DEMO-002")), 200)) {
            var report = stub.client().run("tools", "rules", "unknown");
            check("DUPLICATE_QUERY".equals(report.get("stopReason")), "重复查询被客户端停止");
            check(((List<?>) report.get("toolEvents")).size() == 1, "被拒绝的查询不执行");
        }
        String invalid = tool("c1", "DEMO-002").replace("getIncidentReport", "deleteFile");
        try (Stub stub = new Stub(List.of(invalid), 200)) {
            var report = stub.client().run("tools", "rules", "unknown");
            check("INVALID_TOOL_CALL".equals(report.get("stopReason")), "不执行模型自选的未知工具");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "非法批次没有工具副作用");
        }
        String badArgs = tool("c1", "DEMO-002").replace("\\\"DEMO-002\\\"", "123");
        try (Stub stub = new Stub(List.of(badArgs), 200)) {
            check("INVALID_TOOL_ARGUMENTS".equals(stub.client().run("tools", "rules", "unknown").get("stopReason")),
                    "不将数字参数自动当作报告编号");
        }
        try (Stub stub = new Stub(List.of(completion("未完成", "length")), 200)) {
            check("INCOMPLETE_ANSWER".equals(stub.client().run("chat", "rules", "unknown").get("stopReason")),
                    "被 token 上限截断不能记为完成");
        }
        try (Stub stub = new Stub(List.of("{broken"), 200)) {
            check("INVALID_RESPONSE_JSON".equals(stub.client().run("chat", "rules", "unknown").get("stopReason")),
                    "无效服务响应转为固定错误码");
        }
        for (int status : List.of(401, 429, 500)) {
            try (Stub stub = new Stub(List.of("remote error " + Stub.KEY), status)) {
                var report = stub.client().run("chat", "rules", "unknown");
                check(("HTTP_" + status).equals(report.get("stopReason")), "HTTP 错误保留状态：" + status);
                check(stub.requests.size() == 1, "HTTP 错误不自动重试：" + status);
                check(!DeepSeekPromptLab.JSON.writeValueAsString(report).contains(Stub.KEY), "不记录远端错误正文");
            }
        }
        try (Stub stub = new Stub(List.of(completion(Stub.KEY, "stop")), 200)) {
            var report = stub.client().run("chat", "rules", "unknown");
            check("REJECTED_RESPONSE".equals(report.get("stopReason")), "拒绝服务意外回显凭据");
            check(!DeepSeekPromptLab.JSON.writeValueAsString(report).contains(Stub.KEY), "回显凭据不进入报告");
        }
        try (Stub stub = new Stub(List.of("{\"data\":[{\"id\":\"deepseek-flash\"}]}"), 200)) {
            check(Boolean.TRUE.equals(stub.client().run("check", "rules", "unknown").get("passed")), "GET 模型列表检查");
            check("GET /models".equals(stub.paths.getFirst()), "连通检查不发送生成请求");
        }
        check(!DeepSeekPromptLab.verify("unknown", unknown + " trailing", List.of()).isEmpty(), "拒绝答案尾随文本");
        check(DeepSeekPromptLab.verify("unknown", answer("ANSWER", "DEMO-002", "接口请求超时", "部分请求失败", "0"),
                List.of()).contains("ANSWER_FIELDS"), "未知值填零被字段核验拒绝");
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=compare", "--repeats=4"}), "限制比较预算");
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=check", "--case=unknown"}), "拒绝模式不适用参数");
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=tools", "--mode=chat"}), "拒绝重复参数");
        rejects(() -> DeepSeekPromptLab.officialBase("https://api.deepseek.com.evil.test"), "拒绝非官方凭据接收主机");
        rejects(() -> DeepSeekPromptLab.officialBase("http://api.deepseek.com"), "拒绝明文传输凭据");
        check("https://api.deepseek.com/v1".equals(DeepSeekPromptLab.officialBase("https://api.deepseek.com/v1/")
                .toString()), "支持官方 /v1 基础路径");
        System.out.println("PASS: " + assertions + " 项本机协议与边界断言；未调用线上模型。");
    }

    /**
     * 构造脚本化的工具请求；调用参数按 Chat Completions 协议编码成 JSON 字符串。
     *
     * @param id 调用 ID
     * @param incidentId 教学编号
     * @return 替身的 JSON 响应
     */
    private static String tool(String id, String incidentId) {
        return DeepSeekPromptLab.JSON.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", "tool_calls", "message", Map.of("role", "assistant", "content", "",
                        "tool_calls", List.of(Map.of("id", id, "type", "function", "function", Map.of(
                                "name", "getIncidentReport", "arguments", "{\"incidentId\":\"" + incidentId + "\"}")))))),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5)));
    }

    /**
     * 构造脚本化的候选响应。
     *
     * @param content 候选正文
     * @param finish 结束原因
     * @return 替身的 JSON 响应
     */
    private static String completion(String content, String finish) {
        return DeepSeekPromptLab.JSON.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", finish, "message", Map.of("role", "assistant", "content", content)))));
    }

    /**
     * 构造固定字段契约的教学答案；以 null 保留未知值。
     *
     * @param status 任务结果状态
     * @param id 编号或 null
     * @param symptom 现象或 null
     * @param impact 影响或 null
     * @param recovery 恢复时间或 null
     * @return 答案 JSON
     */
    private static String answer(String status, String id, String symptom, String impact, String recovery) {
        var result = DeepSeekPromptLab.JSON.createObjectNode();
        result.put("status", status);
        result.put("incidentId", id);
        result.put("symptom", symptom);
        result.put("impact", impact);
        result.put("recoveryTime", recovery);
        return result.toString();
    }

    /**
     * 执行并累计一条断言。
     *
     * @param condition 预期成立的条件
     * @param label 失败时的定位信息
     */
    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        assertions++;
    }

    /**
     * 检查本地输入在发请求之前被拒绝。
     *
     * @param action 待拒绝的操作
     * @param label 断言描述
     */
    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, label);
            return;
        }
        throw new AssertionError(label);
    }

    /** 顺序返回预设响应的回环 HTTP 服务，不代表 DeepSeek 的实际行为。 */
    private static final class Stub implements AutoCloseable {
        /** 无效教学占位，不是线上凭据。 */
        private static final String KEY = "protocol-check-not-a-real-key";

        /** 每个检查场景独立占用临时回环端口。 */
        private final HttpServer server;

        /** 按服务接收顺序记录请求正文。 */
        private final List<JsonNode> requests = new ArrayList<>();

        /** 按服务接收顺序记录方法和路径。 */
        private final List<String> paths = new ArrayList<>();

        /**
         * 启动本机服务，超出脚本响应数量的调用返回 500。
         *
         * @param responses 各次请求的预设响应正文
         * @param status 各次请求的 HTTP 状态
         * @throws IOException 无法绑定回环端口
         */
        private Stub(List<String> responses, int status) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int index = paths.size();
                requests.add(body.isBlank() ? DeepSeekPromptLab.JSON.createObjectNode() : DeepSeekPromptLab.JSON.readTree(body));
                paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                byte[] output = (index < responses.size() ? responses.get(index) : "unexpected request")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(index < responses.size() ? status : 500, output.length);
                try (var stream = exchange.getResponseBody()) {
                    stream.write(output);
                }
            });
            server.start();
        }

        /**
         * 创建指向本场景回环服务的客户端，不经过真实入口的官方主机校验。
         *
         * @return 只访问本机的实验客户端
         */
        private DeepSeekPromptLab client() {
            return new DeepSeekPromptLab(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), KEY,
                    "deepseek-flash");
        }

        /** 停止本场景 HTTP 服务，释放临时端口。 */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
