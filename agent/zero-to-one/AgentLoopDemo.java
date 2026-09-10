import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Java 21 离线教学：预设决策模拟模型，只演示控制流，不访问网络或文件。 */
public class AgentLoopDemo {
    /** 一次工具请求；id 用于关联结果，query 是长度不超过 80 的检索词。 */
    record Call(String id, String name, String query) {}
    /** 一次决策：call 与 answer 必须且只能提供一个；answer 为最终展示文本。 */
    record Decision(Call call, String answer) {}
    /** 工具观察；callId 关联请求，ok 区分执行成功与错误，text 为有界返回内容。 */
    record Observation(String callId, boolean ok, String text) {}
    /** 单次运行输出；status 为 DONE 或 LIMIT，trace 是调用与结果的可观察记录。 */
    record Run(String status, String answer, List<String> trace) {}

    static Observation execute(Call call) {
        if (!"search_notes".equals(call.name())) {
            return new Observation(call.id(), false, "TOOL_DENIED: only search_notes is allowed");
        }
        if (call.query() == null || call.query().isBlank() || call.query().length() > 80) {
            return new Observation(call.id(), false, "INVALID_ARGUMENT: query length must be 1..80");
        }
        // ponytail: 固定三条资料仅用于控制流教学；需要真实检索时再换成受限文档搜索。
        String query = call.query().strip().toLowerCase(Locale.ROOT);
        List<String> hits = List.of("study-notes/llm.md", "study-notes/rag.md",
                "study-notes/java-agent.md").stream()
                .filter(path -> path.toLowerCase(Locale.ROOT).contains(query)).toList();
        return new Observation(call.id(), true,
                hits.isEmpty() ? "NO_MATCH" : String.join(", ", hits));
    }

    static Run run(Function<List<Observation>, Decision> model, int maxSteps) {
        if (maxSteps < 1 || maxSteps > 20) {
            throw new IllegalArgumentException("maxSteps must be 1..20");
        }
        List<Observation> observations = new ArrayList<>();
        List<String> trace = new ArrayList<>();
        for (int step = 1; step <= maxSteps; step++) {
            Decision decision = model.apply(List.copyOf(observations));
            if (decision == null || (decision.call() == null) == (decision.answer() == null)) {
                throw new IllegalArgumentException("Expected exactly one call or answer");
            }
            if (decision.answer() != null) {
                if (decision.answer().isBlank()) throw new IllegalArgumentException("Empty answer");
                trace.add("FINAL " + decision.answer());
                return new Run("DONE", decision.answer(), List.copyOf(trace));
            }
            Call call = decision.call();
            if (call.id() == null || call.id().isBlank()
                    || observations.stream().anyMatch(item -> item.callId().equals(call.id()))) {
                throw new IllegalArgumentException("Call id must be nonblank and unique");
            }
            trace.add("CALL " + call);
            Observation result = execute(call);
            observations.add(result);
            trace.add("RESULT " + result);
        }
        return new Run("LIMIT", "Step budget exhausted; task is incomplete.", List.copyOf(trace));
    }

    static Decision scriptedModel(List<Observation> history, String query) {
        if (history.isEmpty()) return new Decision(new Call("call-1", "search_notes", query), null);
        Observation last = history.getLast();
        String answer = !last.ok() ? "Tool failed: " + last.text()
                : "NO_MATCH".equals(last.text()) ? "No matching source; more information is needed."
                : "Read this source: " + last.text();
        return new Decision(null, answer);
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--check".equals(args[0])) {
            check();
            return;
        }
        String query = args.length == 0 ? "Java" : args[0];
        Run result = run(history -> scriptedModel(history, query), 3);
        result.trace().forEach(System.out::println);
        System.out.println("STATUS " + result.status());
    }

    /** 可重复的离线检查，不验证真实模型的选工具能力。使用 -ea 运行。 */
    static void check() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (!assertionsEnabled) throw new IllegalStateException("Run with java -ea");
        Run found = run(history -> scriptedModel(history, "Java"), 3);
        assert found.status().equals("DONE") && found.answer().contains("study-notes/java-agent.md");
        assert found.trace().size() == 3 && found.trace().get(1).contains("callId=call-1");
        assert run(h -> scriptedModel(h, "Kubernetes"), 3).answer().startsWith("No matching");
        assert run(h -> scriptedModel(h, " "), 3).answer().contains("INVALID_ARGUMENT");
        assert !execute(new Call("x", "delete_file", "README.md")).ok();
        assert !execute(new Call("x", "search_notes", "x".repeat(81))).ok();
        Run denied = run(h -> h.isEmpty() ? new Decision(new Call("x", "delete_file", "a"), null)
                : new Decision(null, h.getLast().text()), 3);
        assert denied.answer().contains("TOOL_DENIED");
        Run looping = run(h -> new Decision(new Call("c" + h.size(), "search_notes", "RAG"), null), 3);
        assert looping.status().equals("LIMIT") && looping.trace().size() == 6;
        assert run(h -> new Decision(null, "Hello"), 1).trace().size() == 1;
        try {
            run(h -> new Decision(null, null), 1);
            throw new AssertionError("Malformed decision accepted");
        } catch (IllegalArgumentException expected) { /* malformed decision fails closed */ }
        try {
            run(h -> new Decision(new Call("same", "search_notes", "RAG"), null), 3);
            throw new AssertionError("Duplicate call id accepted");
        } catch (IllegalArgumentException expected) { /* duplicate request rejected */ }
        System.out.println("PASS: offline loop, results, denial, validation and step budget");
    }
}
