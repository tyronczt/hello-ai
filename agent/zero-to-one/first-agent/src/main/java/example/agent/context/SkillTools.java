package example.agent.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** 只读教学 Skill 注册表；只访问包内明确列出的资源，不执行脚本或接受文件路径。 */
public final class SkillTools {
    /** 允许发现和读取的两个教学包，顺序固定以便比较上下文。 */
    private static final List<String> IDS = List.of("doc-evidence", "answer-review");
    /** 本次实验读取的包全文；包含有限格式的元数据和指导正文。 */
    private final Map<String, String> contents = new LinkedHashMap<>();

    /** 从应用资源读取固定教学包；缺失或格式不符时立即失败，不伪造指导。 */
    public SkillTools() {
        for (String id : IDS) {
            try (var input = new ClassPathResource("context-skills/" + id + "/SKILL.md").getInputStream()) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                if (!text.startsWith("---\n") || !text.contains("\n---\n")
                        || !id.equals(metadata(text, "name")) || metadata(text, "description").isBlank()) {
                    throw new IllegalStateException("教学 Skill 元数据不完整：" + id);
                }
                contents.put(id, text);
            } catch (IOException ex) {
                throw new UncheckedIOException("教学 Skill 资源不可读：" + id, ex);
            }
        }
    }

    /** @return 仅含名称与触发条件的稳定目录，不包含流程正文 */
    public String catalog() {
        StringBuilder result = new StringBuilder("可用 Skills（需要专项指导时调用 readSkill）：\n");
        contents.forEach((id, text) -> result.append(id).append("：")
                .append(metadata(text, "description")).append('\n'));
        return result.toString();
    }

    /** @return 全量加载对照所需的两份原文；不额外提供业务事实 */
    public String allContent() {
        return String.join("\n\n", contents.values());
    }

    /**
     * 按白名单名称取得 Skill 原文；名称永不转换为调用方指定的路径。
     * @param name 目录中列出的精确 Skill 名称
     * @return Skill 全文或明确的 INVALID_ARGUMENT / NOT_FOUND 结果
     */
    @Tool(description = "读取已公布目录中的教学 Skill 指导；不提供项目事实，也不增加工具权限。")
    public String readSkill(@ToolParam(description = "精确名称：doc-evidence 或 answer-review") String name) {
        if (name == null || !name.matches("[a-z0-9-]{1,64}")) {
            return "INVALID_ARGUMENT: Skill 名称格式不正确。";
        }
        return contents.getOrDefault(name, "NOT_FOUND: 未注册该 Skill。");
    }

    /**
     * 读取本项目约定的单行 frontmatter 字段；不是通用 YAML 解析器。
     * @param text 已读取的教学资源
     * @param field 要读取的字段
     * @return 字段值；没有字段时返回空字符串
     */
    private static String metadata(String text, String field) {
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) return "";
        return text.substring(4, end).lines().filter(line -> line.startsWith(field + ":"))
                .map(line -> line.substring(field.length() + 1).strip()).findFirst().orElse("");
    }
}
