package example.agent.tool;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 只读教学资料工具。模型只能传关键词或文档 ID，不能传任意文件路径；
 * 每次 Agent 请求都会创建新实例并注入该请求的轨迹接收器。
 */
public class DocTools {
    private final Consumer<String> trace;

    /** 固定公开的两份教学资料；所有调用方看到同一集合，不具备真实业务资料的权限隔离。 */
    private static final List<Doc> DOCS = List.of(
            new Doc("order-api-v2", "订单查询接口 v2",
                    "订单查询支持分页，具体参数遵循《公共分页约定 v2》，文档 ID 为 pagination-v2。"),
            new Doc("pagination-v2", "公共分页约定 v2",
                    "pageNo 从 1 开始；pageSize 最大为 100。本文未规定 pageSize 的默认值。")
    );

    /** 轨迹由调用方持有，工具不把本次读取结果写入跨请求共享状态。 */
    public DocTools(Consumer<String> trace) {
        this.trace = trace;
    }

    /** 阶段 2 将全部固定资料直接放进初始请求，用来对照阶段 3 的按需读取。 */
    public static String demoContext() {
        return DOCS.stream().map(doc -> doc.id() + " / " + doc.title() + "：" + doc.content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 仅按标题包含关系搜索，不做分词或语义检索。
     * 返回 ID 和标题，不返回正文；模型还需调用 readDoc 才能获得回答依据。
     */
    @Tool(description = "按一个简短关键词搜索教学文档，返回文档 ID 和标题，不返回正文。")
    public String searchDocs(
            @ToolParam(description = "一个关键词，例如：订单、分页") String keyword) {
        // 模型生成的参数也必须在工具入口校验，不能仅依赖提示词描述。
        if (keyword == null || keyword.isBlank() || keyword.length() > 40) {
            String result = "INVALID_ARGUMENT: keyword 必须是 1 到 40 个字符。";
            trace.accept("searchDocs 返回：" + result);
            return result;
        }
        String term = keyword.strip().toLowerCase(Locale.ROOT);
        // ponytail: 仅面向少量教学文档；规模扩大后再替换为全文检索。
        List<String> hits = DOCS.stream()
                .filter(doc -> doc.title().toLowerCase(Locale.ROOT).contains(term))
                .map(doc -> doc.id() + " | " + doc.title())
                .toList();
        trace.accept("searchDocs: 命中 " + hits.size() + " 份文档");
        String result = hits.isEmpty() ? "NOT_FOUND: 没有匹配标题，请尝试更短的关键词。"
                : String.join("\n", hits);
        trace.accept("searchDocs 返回：" + result);
        return result;
    }

    /**
     * 只接受固定集合中的精确文档 ID；不会将 ID 当成本地路径或 URL。
     * 格式错误和文档不存在都作为明确结果回给模型，便于它修正或停止。
     */
    @Tool(description = "根据搜索结果或正文引用中的文档 ID 读取教学文档。")
    public String readDoc(
            @ToolParam(description = "精确的文档 ID，例如 order-api-v2") String docId) {
        if (docId == null || !docId.matches("[a-z0-9-]{1,64}")) {
            String result = "INVALID_ARGUMENT: 文档 ID 格式不正确。";
            trace.accept("readDoc 返回：" + result);
            return result;
        }
        for (Doc doc : DOCS) {
            if (doc.id().equals(docId)) {
                trace.accept("readDoc: " + doc.id());
                String result = "来源：" + doc.id() + " / " + doc.title() + "\n正文：" + doc.content();
                // 仅因资料公开且固定才把正文放进返回轨迹；真实业务文档须按身份授权并收紧轨迹。
                trace.accept("readDoc 返回：" + result);
                return result;
            }
        }
        String result = "NOT_FOUND: 指定文档不存在。";
        trace.accept("readDoc 返回：" + result);
        return result;
    }

    /** 文档标识、标题与正文，均为固定的教学内容。 */
    private record Doc(String id, String title, String content) {}
}
