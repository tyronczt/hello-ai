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
        checkComparisonIsolation(unknown);
        checkBatchBudgets();
        checkModelListResults();
        checkStrictJsonAndCredentialEcho();
        checkLearningControls();
        System.out.println("PASS: " + assertions + " 项本机协议与边界断言；未调用线上模型。");
    }

    /**
     * 检查比较编排中的任务隔离、提示词顺序、实际运行数量和服务错误停止。
     *
     * @param unknown 概率未知用例对应的完整期望答案 JSON
     * @throws IOException 本机服务无法启动或关闭
     */
    private static void checkComparisonIsolation(String unknown) throws IOException {
        List<String> responses = List.of(
                tool("c1", "DEMO-002"), completion(unknown, "stop"),
                tool("c1", "DEMO-002"), completion(unknown, "stop"),
                tool("c1", "DEMO-002"), completion(unknown, "stop"));
        try (Stub stub = new Stub(responses, 200)) {
            var reports = stub.client().runExperiment(DeepSeekPromptLab.parse(
                    new String[]{"--mode=compare", "--case=unknown", "--repeats=1"}));
            check(reports.size() == 3, "比较实际执行三项独立任务");
            check(stub.requests.size() == 6, "各方案分别完成一次工具往返");
            check(reports.stream().map(report -> report.get("promptMode")).toList()
                    .equals(List.of("basic", "rules", "examples")), "保持固定的提示词比较顺序");
            for (int index = 0; index < reports.size(); index++) {
                check(Boolean.TRUE.equals(reports.get(index).get("passed")), "同一编号在各独立任务中可分别查询");
                check(Integer.valueOf(1).equals(reports.get(index).get("repeat")), "报告保留实际重复序号");
                check(stub.requests.get(index * 2).path("messages").size() == 2,
                        "新方案首轮不继承上一方案的历史");
                check(stub.requests.get(index * 2 + 1).path("messages").size() == 4,
                        "每项任务只回填自身的 assistant/tool 消息对");
            }
        }
        try (Stub stub = new Stub(List.of("rate limited"), 429)) {
            var reports = stub.client().runExperiment(Map.of("mode", "compare"));
            check(reports.size() == 1, "限流时仅保留已执行任务，不填补其他比较结果");
            check(stub.requests.size() == 1, "限流后不继续请求其他提示词方案");
            check("HTTP_429".equals(reports.getFirst().get("stopReason")), "保留停止整次比较的服务错误码");
        }
    }

    /**
     * 检查整批预检与工具预算，确认拒绝批次没有任何部分执行。
     *
     * @throws IOException 本机服务无法启动或关闭
     */
    private static void checkBatchBudgets() throws IOException {
        String invalidSecond = toolBatch(tool("c1", "DEMO-001"),
                tool("c2", "DEMO-002").replace("getIncidentReport", "deleteFile"));
        try (Stub stub = new Stub(List.of(invalidSecond), 200)) {
            var report = stub.client().run("tools", "rules", "complete");
            check("INVALID_TOOL_CALL".equals(report.get("stopReason")), "第二项非法时拒绝整个批次");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "合法第一项也不提前执行");
        }
        String oversized = toolBatch(tool("c1", "DEMO-001"), tool("c2", "DEMO-002"),
                tool("c3", "DEMO-404"), tool("c4", "DEMO-503"));
        try (Stub stub = new Stub(List.of(oversized), 200)) {
            var report = stub.client().run("tools", "rules", "complete");
            check("TOOL_BUDGET".equals(report.get("stopReason")), "整批调用数超过预算时停止");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "超预算批次不做部分执行");
        }
        try (Stub stub = new Stub(List.of(tool("c1", "DEMO-001"), tool("c2", "DEMO-002"),
                tool("c3", "DEMO-404"), tool("c4", "DEMO-503")), 200)) {
            var report = stub.client().run("tools", "rules", "complete");
            check("TOOL_BUDGET".equals(report.get("stopReason")), "第四轮不再执行无法回填的工具");
            check(((List<?>) report.get("toolEvents")).size() == 3, "仅保留预算内已执行的三项工具事件");
            check(stub.requests.size() == 4, "模型请求数量没有超过四轮");
        }
        String duplicateId = toolBatch(tool("c1", "DEMO-001"), tool("c1", "DEMO-002"));
        try (Stub stub = new Stub(List.of(duplicateId), 200)) {
            var report = stub.client().run("tools", "rules", "complete");
            check("INVALID_TOOL_CALL".equals(report.get("stopReason")), "拒绝同一批次的重复调用 ID");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "重复调用 ID 批次不执行");
        }
        String duplicateQuery = toolBatch(tool("c1", "DEMO-001"), tool("c2", "DEMO-001"));
        try (Stub stub = new Stub(List.of(duplicateQuery), 200)) {
            var report = stub.client().run("tools", "rules", "complete");
            check("DUPLICATE_QUERY".equals(report.get("stopReason")), "拒绝同批次不同 ID 对同一编号的重复查询");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "重复查询批次不执行");
        }
    }

    /**
     * 检查模型不可见和模型列表协议错误，避免把 HTTP 200 直接记为阶段通过。
     *
     * @throws IOException 本机服务无法启动或关闭
     */
    private static void checkModelListResults() throws IOException {
        try (Stub stub = new Stub(List.of("{\"data\":[]}"), 200)) {
            var report = stub.client().run("check", "rules", "unknown");
            check("CHECKED".equals(report.get("status")), "取得合法列表时保留已检查状态");
            check(Boolean.FALSE.equals(report.get("passed")), "配置模型未列出时检查不通过");
            check(((List<?>) report.get("checks")).contains("MODEL_NOT_LISTED"), "模型未列出有独立错误码");
        }
        try (Stub stub = new Stub(List.of("{\"data\":{}}"), 200)) {
            check("INVALID_MODEL_LIST".equals(stub.client().run("check", "rules", "unknown").get("stopReason")),
                    "拒绝非数组的模型列表");
        }
    }

    /**
     * 检查歧义 JSON 与解码后凭据回显，覆盖响应解析提取后的拒绝路径。
     *
     * @throws IOException 本机服务无法启动或关闭
     */
    private static void checkStrictJsonAndCredentialEcho() throws IOException {
        String duplicateArguments = toolWithArguments("c1", "{\"incidentId\":\"DEMO-002\","
                + "\"incidentId\":\"DEMO-001\"}");
        try (Stub stub = new Stub(List.of(duplicateArguments), 200)) {
            var report = stub.client().run("tools", "rules", "unknown");
            check("INVALID_TOOL_ARGUMENTS".equals(report.get("stopReason")), "拒绝工具参数中的重复 JSON 键");
            check(((List<?>) report.get("toolEvents")).isEmpty(), "歧义 JSON 没有进入工具执行");
        }
        String trailingArguments = toolWithArguments("c1", "{\"incidentId\":\"DEMO-002\"} trailing");
        try (Stub stub = new Stub(List.of(trailingArguments), 200)) {
            check("INVALID_TOOL_ARGUMENTS".equals(stub.client().run("tools", "rules", "unknown").get("stopReason")),
                    "拒绝工具参数 JSON 后的尾随内容");
        }
        String escapedKey = completion(Stub.KEY, "stop").replace(Stub.KEY,
                "\\u0070rotocol-check-not-a-real-key");
        try (Stub stub = new Stub(List.of(escapedKey), 200)) {
            var report = stub.client().run("chat", "rules", "unknown");
            check("REJECTED_RESPONSE".equals(report.get("stopReason")), "解码后识别 Unicode 转义的凭据回显");
            check(!DeepSeekPromptLab.JSON.writeValueAsString(report).contains(Stub.KEY), "解码后的凭据不入报告");
        }
        check(DeepSeekPromptLab.verify("missing-id", "{\"status\":\"NEED_ID\",\"status\":\"ANSWER\"}",
                List.of()).contains("ANSWER_JSON"), "拒绝候选中的重复 JSON 键");
    }

    /**
     * 检查读者控制项和可读对照，全部使用明确的本机替身数据。
     *
     * @throws IOException 本机替身无法启动或关闭
     */
    private static void checkLearningControls() throws IOException {
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=tools", "--system=改规则"}),
                "自定义系统消息只适用于 chat");
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=check", "--predict=预测"}),
                "连接阶段不记录学习预测");
        rejects(() -> DeepSeekPromptLab.parse(new String[]{"--mode=chat", "--system=" + "长".repeat(2001)}),
                "自定义规则有长度边界");
        try (Stub stub = new Stub(List.of(completion("替身响应，不是模型实测。", "stop")), 200)) {
            var reports = stub.client().runExperiment(DeepSeekPromptLab.parse(new String[]{
                    "--mode=chat", "--system=只用两项列表回答。", "--predict=会有两项"}));
            check("只用两项列表回答。".equals(stub.requests.getFirst().path("messages").get(0)
                    .path("content").asString()), "系统规则通过真实请求消息传出");
            check("会有两项".equals(reports.getFirst().get("prediction")), "保留运行前预测");
        }
        try (Stub stub = new Stub(List.of(), 200)) {
            rejects(() -> stub.client().runExperiment(Map.of("mode", "chat", "predict", Stub.KEY)),
                    "预测不得把请求凭据带入报告");
            check(stub.requests.isEmpty(), "凭据选项在网络调用前被拒绝");
        }
        String notFound = answer("NOT_FOUND", "DEMO-404", null, null, null);
        String reordered = "{\"recoveryTime\":null,\"impact\":null,\"symptom\":null,"
                + "\"incidentId\":\"DEMO-404\",\"status\":\"NOT_FOUND\"}";
        try (Stub stub = new Stub(List.of(tool("c1", "DEMO-404"), completion(notFound, "stop"),
                tool("c1", "DEMO-404"), completion(reordered, "stop"),
                tool("c1", "DEMO-404"), completion(notFound, "stop")), 200)) {
            var reports = stub.client().runExperiment(Map.of("mode", "compare", "predict", "可能没有差异"));
            check(reports.stream().allMatch(report -> "not-found".equals(report.get("case"))),
                    "默认比较用未出现在示例里的 not-found");
            check(reports.stream().allMatch(report -> Boolean.TRUE.equals(report.get("passed"))),
                    "JSON 字段顺序不同仍通过结构核验");
            String markdown = DeepSeekPromptLab.comparisonMarkdown(reports);
            check(markdown.contains("文本相等=false；JSON 结构相等=true"), "区分文本差异和 JSON 结构差异");
            check(markdown.contains("examples：1/1"), "通过分母取自已执行任务");
            check(markdown.contains(reordered)
                    && markdown.contains("| recoveryTime | null | null | null |"),
                    "保留原始候选，并排展示未知字段");
            check(markdown.contains("可能没有差异"), "可读对照展示预测");
        }
        try (Stub stub = new Stub(List.of("rate limited"), 429)) {
            String markdown = DeepSeekPromptLab.comparisonMarkdown(
                    stub.client().runExperiment(Map.of("mode", "compare")));
            check(markdown.contains("basic：0/1") && markdown.contains("rules：0/0")
                    && markdown.contains("examples：0/0")
                    && markdown.contains("| status | 无法解析 | 未运行 | 未运行 |"),
                    "中断比较不把未执行方案计为失败，字段表区分未运行与无有效候选");
        }
    }

    /**
     * 构造脚本化的工具请求；调用参数按 Chat Completions 协议编码成 JSON 字符串。
     *
     * @param id 调用 ID
     * @param incidentId 教学编号
     * @return 替身的 JSON 响应
     */
    private static String tool(String id, String incidentId) {
        return toolWithArguments(id, DeepSeekPromptLab.JSON.writeValueAsString(Map.of("incidentId", incidentId)));
    }

    /**
     * 为参数拒绝检查构造工具请求，允许传入刻意非法的参数 JSON 字符串。
     *
     * @param id 调用 ID
     * @param arguments 准备交给客户端校验的参数字符串
     * @return 替身 JSON 响应
     */
    private static String toolWithArguments(String id, String arguments) {
        return DeepSeekPromptLab.JSON.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", "tool_calls", "message", Map.of("role", "assistant", "content", "",
                        "tool_calls", List.of(Map.of("id", id, "type", "function", "function", Map.of(
                                "name", "getIncidentReport", "arguments", arguments)))))),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5)));
    }

    /**
     * 将单调用响应里的请求组合为一个批次，不执行任何真实工具。
     *
     * @param responses 各含一个工具调用的脚本化响应
     * @return 含多项调用的脚本化响应
     */
    private static String toolBatch(String... responses) {
        List<JsonNode> calls = new ArrayList<>();
        for (String response : responses) {
            calls.add(DeepSeekPromptLab.JSON.readTree(response).path("choices").get(0)
                    .path("message").path("tool_calls").get(0));
        }
        return DeepSeekPromptLab.JSON.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", "tool_calls", "message", Map.of("role", "assistant", "content", "",
                        "tool_calls", calls)))));
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
                requests.add(body.isBlank() ? DeepSeekPromptLab.JSON.createObjectNode()
                        : DeepSeekPromptLab.JSON.readTree(body));
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
