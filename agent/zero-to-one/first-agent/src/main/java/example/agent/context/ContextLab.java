package example.agent.context;

import example.agent.dto.AgentResultDTO;
import example.agent.dto.AskDTO;
import example.agent.service.DocAgent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * 第 03 篇的可运行实验：本机脚本验证消息与工具机制，显式 live 模式观察真实模型。
 * 固定公开教学问题，避免把任意用户输入自动写入报告。
 */
public final class ContextLab {
    /** 所有策略共用的固定公开问题，控制对照中的任务变量。 */
    public static final String QUESTION = "订单查询的分页参数怎么传？请给出文档依据，并说明默认值是否规定。";
    /** 报告和自检证据编码，复用项目已有 JSON 库。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 工具类不持有全局实验状态。 */
    private ContextLab() {
    }

    /**
     * 无需密钥地运行本机协议实验，并核验失败与预算边界。
     * @param selection all 或一个 ContextMode 名称
     * @throws IOException 本机服务或实验记录无法访问
     */
    public static void offline(String selection) throws IOException {
        List<ContextMode> modes = selection.equals("all")
                ? List.of(ContextMode.values()) : List.of(ContextMode.parse(selection));
        List<String> checks = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        Map<ContextMode, ContextCapture> captures = new java.util.EnumMap<>(ContextMode.class);
        System.out.println("离线脚本实验：真实执行本机 HTTP、Spring AI 适配器和 Java 工具；模型决策与答案为预设。");
        for (ContextMode mode : modes) {
            try (var server = new ScriptedChatServer(mode)) {
                var model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                        .baseUrl(server.baseUrl()).apiKey("offline-fixture-only")
                        .model("deepseek-flash").timeout(Duration.ofSeconds(10)).maxRetries(0).build()).build();
                var capture = new ContextCapture(model, true);
                var result = new DocAgent(capture).runContext(new AskDTO(QUESTION), mode);
                Path output = capture.write(mode, result, server.requests());
                outputs.add(output.toString());
                server.verifyHealthy();
                verifyRun(mode, result, capture, server.requests().size(), checks);
                captures.put(mode, capture);
                System.out.println(mode + "：校验通过；报告 " + output);
            }
        }
        if (captures.containsKey(ContextMode.STATE) && captures.containsKey(ContextMode.COMPACT)) {
            int full = (int) captures.get(ContextMode.STATE).rounds().getLast().get("messageFieldChars");
            int compact = (int) captures.get(ContextMode.COMPACT).rounds().getLast().get("messageFieldChars");
            check(compact < full, "相同脚本下，压缩后末轮字段字符数减少", checks);
        }
        verifyBoundaries(checks);
        var prefix = prefixExperiment(checks);
        Path base = Path.of("target", "context-lab").toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path summary = Files.createTempFile(base, "checks-", ".json");
        Files.writeString(summary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("kind", "OFFLINE_CHECKS", "passed", checks.size(), "checks", checks,
                        "reports", outputs, "prefixExperiment", prefix)), StandardCharsets.UTF_8);
        System.out.println("离线断言通过：" + checks.size() + " 项；清单 " + summary);
        System.out.println("离线结果不证明真实模型质量、缓存命中、Token 节省或线上成功率。");
    }

    /**
     * 使用 Spring 已配置的模型执行一次显式实验，不自动批量调用线上服务。
     * @param model 真实模型客户端
     * @param selection 一个 ContextMode 名称
     * @throws IOException 教学报告无法写入
     */
    public static void live(ChatModel model, String selection) throws IOException {
        ContextMode mode = ContextMode.parse(selection);
        var capture = new ContextCapture(model, false);
        var result = new DocAgent(capture).runContext(new AskDTO(QUESTION), mode);
        Path output = capture.write(mode, result, List.of());
        System.out.println("真实模型实验 " + mode + "：" + result.status() + "；报告 " + output);
        System.out.println("候选答案仍需用正文核对；COMPLETED 只表示模型给出了非空回复。");
        if (result.status() != AgentResultDTO.Status.COMPLETED) {
            throw new IllegalStateException("模型实验受控停止；请查看报告中的停止原因与轨迹。");
        }
    }

    /**
     * 检查本机实验的实际请求、原始资料、失败记录与策略差异。
     * @param mode 本次模式
     * @param result Agent 运行结果
     * @param capture 每次模型调用前后的记录
     * @param wireCount 本机实际收到的 HTTP 请求数
     * @param checks 本次验证清单
     */
    private static void verifyRun(ContextMode mode, AgentResultDTO result, ContextCapture capture,
                                  int wireCount, List<String> checks) {
        String label = mode + "：";
        var rounds = capture.rounds();
        check(result.status() == AgentResultDTO.Status.COMPLETED, label + "脚本循环完整结束", checks);
        check(rounds.size() == 6 && wireCount == 6, label + "六次模型调用均到达本机 HTTP 服务", checks);
        check(rounds.stream().map(row -> row.get("systemSha256")).distinct().count() == 1,
                label + "系统规则在各轮保持稳定", checks);
        check(rounds.stream().allMatch(row -> row.get("usage") == null), label + "不伪造服务商用量", checks);
        String first = JSON.writeValueAsString(rounds.getFirst().get("messages"));
        String fourth = JSON.writeValueAsString(rounds.get(3).get("messages"));
        String last = JSON.writeValueAsString(rounds.getLast().get("messages"));
        check(!first.contains("pageNo 从 1 开始"), label + "首轮未提前注入文档事实", checks);
        check(fourth.contains("pageNo 从 1 开始") && fourth.contains("pageSize 最大为 100"),
                label + "实际工具正文进入后续模型请求", checks);
        check(last.contains("未规定 pageSize") && last.contains("pagination-v2"),
                label + "末轮保留未知项和文档来源", checks);
        check(last.contains("NOT_FOUND"), label + "失败返回不会被丢失", checks);
        check(rounds.stream().allMatch(row -> JSON.writeValueAsString(row.get("messages")).contains(QUESTION)),
                label + "每轮保留原始问题", checks);
        if (mode == ContextMode.EAGER) {
            check(first.contains("# 用文档依据回答接口问题") && first.contains("# 核对已有答案"),
                    label + "全量模式预加载两份指导", checks);
        } else if (mode.usesSkills()) {
            check(!first.contains("# 用文档依据回答接口问题") && first.contains("doc-evidence"),
                    label + "首轮只有目录，没有 Skill 正文", checks);
            check(JSON.writeValueAsString(rounds.get(1).get("messages")).contains("# 用文档依据回答接口问题"),
                    label + "readSkill 实际返回的正文进入下一轮", checks);
            check(!last.contains("# 核对已有答案"), label + "未请求的指导不进入上下文", checks);
        }
        if (mode.usesState()) {
            @SuppressWarnings("unchecked")
            var messages = (List<Map<String, Object>>) rounds.getLast().get("messages");
            String state = (String) messages.getLast().get("content");
            check(state.startsWith(ContextSession.STATE_MARKER) && state.contains("剩余工具调用：0"),
                    label + "状态使用 Java 的实际预算计数", checks);
            check(state.contains("已读文档：[order-api-v2, pagination-v2]")
                            && !state.contains("pagination-v2, missing-doc]"),
                    label + "不存在的文档不登记为已读", checks);
        }
        if (mode == ContextMode.COMPACT) {
            check(last.contains(ContextSession.CHECKPOINT_MARKER), label + "产生了可核对的旧工具结果检查点", checks);
        }
    }

    /**
     * 用小型脚本核对非法 Skill、工具配对、整批拒绝、预算和请求隔离。
     * @param checks 验证清单
     */
    private static void verifyBoundaries(List<String> checks) {
        var skills = new SkillTools();
        check(skills.readSkill("../secret").startsWith("INVALID_ARGUMENT"), "Skill 不接受路径穿越", checks);
        check(skills.readSkill("unknown").startsWith("NOT_FOUND"), "未注册 Skill 不读取任意资源", checks);
        List<Message> orphan = List.of(ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("orphan", "readDoc", "data"))).build());
        expectPairFailure(orphan, "孤立工具返回被拒绝", checks);
        var request = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("same", "function", "readDoc", "{\"docId\":\"pagination-v2\"}"))).build();
        expectPairFailure(List.of(request, new UserMessage("不能插在批次中间")), "状态插入未完成批次时被拒绝", checks);
        var duplicate = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("same", "function", "readDoc", "{}"),
                new AssistantMessage.ToolCall("same", "function", "readDoc", "{}"))).build();
        expectPairFailure(List.of(duplicate), "重复调用 ID 被拒绝", checks);
        var session = new ContextSession(ContextMode.STATE);
        session.start(DocAgent.initialMessages(QUESTION, 3), 6, 8);
        boolean rejectedReuse = false;
        try {
            session.start(DocAgent.initialMessages(QUESTION, 3), 6, 8);
        } catch (IllegalStateException ex) {
            rejectedReuse = true;
        }
        check(rejectedReuse, "上下文实例不能跨任务复用", checks);
        ChatModel forbidden = prompt -> response(List.of(
                new AssistantMessage.ToolCall("allowed", "function", "readDoc", "{\"docId\":\"pagination-v2\"}"),
                new AssistantMessage.ToolCall("blocked", "function", "executeShell", "{}")));
        var denied = new DocAgent(forbidden).runContext(new AskDTO(QUESTION), ContextMode.COMPACT);
        check(denied.status() == AgentResultDTO.Status.STOPPED
                && denied.trace().stream().noneMatch(line -> line.startsWith("readDoc:")),
                "包含未授权工具时整批拒绝，合法工具也不先执行", checks);
        List<AssistantMessage.ToolCall> tooMany = new ArrayList<>();
        for (int i = 0; i < 9; i++) tooMany.add(new AssistantMessage.ToolCall(
                "overflow-" + i, "function", "readDoc", "{\"docId\":\"pagination-v2\"}"));
        var overflow = new DocAgent(prompt -> response(tooMany)).runContext(new AskDTO(QUESTION), ContextMode.COMPACT);
        check(overflow.status() == AgentResultDTO.Status.STOPPED
                        && overflow.trace().stream().noneMatch(line -> line.startsWith("readDoc:")),
                "九个工具的批次在执行前超过预算并停止", checks);
        AtomicInteger calls = new AtomicInteger();
        var loop = new DocAgent(prompt -> response(List.of(new AssistantMessage.ToolCall(
                "repeat-" + calls.incrementAndGet(), "function", "readDoc", "{\"docId\":\"pagination-v2\"}"))));
        var limited = loop.runContext(new AskDTO(QUESTION), ContextMode.COMPACT);
        check(calls.get() == 6 && limited.status() == AgentResultDTO.Status.STOPPED
                && limited.trace().stream().filter(line -> line.startsWith("readDoc:")).count() == 5,
                "压缩不重置预算；第六轮请求工具时停止且不执行", checks);
        List<List<Message>> starts = new ArrayList<>();
        var independent = new DocAgent(prompt -> {
            starts.add(List.copyOf(prompt.getInstructions()));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("脚本响应"))));
        });
        independent.runContext(new AskDTO(QUESTION), ContextMode.STATE);
        independent.runContext(new AskDTO(QUESTION), ContextMode.STATE);
        check(starts.size() == 2 && starts.stream().allMatch(messages ->
                messages.getLast().getText().contains("已读文档：[]")
                        && messages.getLast().getText().contains("剩余模型调用：6")),
                "同一 Agent 连续请求各自从空状态开始", checks);
    }

    /**
     * 构造用于边界验证的模型工具请求，不假装是线上结果。
     * @param calls 预设调用列表
     * @return 脚本响应
     */
    private static ChatResponse response(List<AssistantMessage.ToolCall> calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
    }

    /**
     * 验证非法消息确实触发配对检查。
     * @param messages 故意破坏的历史
     * @param label 预期拒绝原因
     * @param checks 验证清单
     */
    private static void expectPairFailure(List<Message> messages, String label, List<String> checks) {
        boolean failed = false;
        try {
            ContextSession.validateToolPairs(messages);
        } catch (IllegalArgumentException ex) {
            failed = true;
        }
        check(failed, label, checks);
    }

    /**
     * 对照动态前缀与末尾状态的公共文本长度，不把布局实验当作缓存命中实验。
     * @param checks 验证清单
     * @return 可复核的文本布局指标
     */
    private static Map<String, Object> prefixExperiment(List<String> checks) {
        String stable = new SystemMessage("固定规则：依据正文回答，未知值明确说明。").getText()
                + "\n固定工具：searchDocs、readDoc\n";
        int early = commonPrefix("轮次=1\n" + stable, "轮次=2\n" + stable);
        int late = commonPrefix(stable + "轮次=1\n", stable + "轮次=2\n");
        check(late > early, "动态信息放后面时，示例文本的公共前缀更长", checks);
        return Map.of("unit", "UTF-16 code units, not tokens", "dynamicFirstCommonChars", early,
                "dynamicLastCommonChars", late, "cacheHit", "unavailable; no provider cache experiment");
    }

    /**
     * 计算两个教学字符串的公共前缀长度。
     * @param first 第一个布局
     * @param second 第二个布局
     * @return 相同的 UTF-16 单元数
     */
    private static int commonPrefix(String first, String second) {
        int index = 0;
        while (index < Math.min(first.length(), second.length()) && first.charAt(index) == second.charAt(index)) index++;
        return index;
    }

    /**
     * 失败立即终止命令并返回非零退出码；成功项才进入清单。
     * @param condition 实际检查结果
     * @param label 可复核的检查说明
     * @param checks 成功项接收列表
     */
    private static void check(boolean condition, String label, List<String> checks) {
        if (!condition) throw new IllegalStateException("离线断言失败：" + label);
        checks.add(label);
    }
}
