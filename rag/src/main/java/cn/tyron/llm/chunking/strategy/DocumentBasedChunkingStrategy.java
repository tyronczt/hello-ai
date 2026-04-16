package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文档结构分块策略
 * 利用标题、表格、页码等文档自身结构进行切分
 */
@Component
public class DocumentBasedChunkingStrategy extends AbstractChunkingStrategy {

    // Markdown 标题正则
    private static final Pattern MARKDOWN_HEADER_PATTERN = 
            Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    
    // HTML 标题标签正则
    private static final Pattern HTML_HEADER_PATTERN = 
            Pattern.compile("<h([1-6])[^>]*>([^<]+)</h[1-6]>", Pattern.CASE_INSENSITIVE);
    
    // 代码块分隔符
    private static final Pattern CODE_BLOCK_PATTERN = 
            Pattern.compile("```[\\w]*\\n(.*?)```", Pattern.DOTALL);

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.DOCUMENT_BASED;
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        String documentType = config.getDocumentType();
        int headerLevel = config.getHeaderLevel();

        switch (documentType.toLowerCase()) {
            case "markdown":
            case "md":
                return chunkMarkdown(text, headerLevel);
            case "html":
            case "htm":
                return chunkHtml(text, headerLevel);
            case "json":
                return chunkJson(text);
            case "code":
                return chunkCode(text);
            default:
                // 默认按段落切分
                return chunkByParagraph(text);
        }
    }

    /**
     * 按 Markdown 标题切分
     */
    private List<String> chunkMarkdown(String text, int headerLevel) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = MARKDOWN_HEADER_PATTERN.matcher(line);
            if (matcher.find()) {
                int level = matcher.group(1).length();
                // 如果当前块不为空且遇到新标题，保存当前块
                if (currentChunk.length() > 0 && level <= headerLevel) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(line).append("\n");
            } else {
                currentChunk.append(line).append("\n");
            }
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 按 HTML 标题切分
     */
    private List<String> chunkHtml(String text, int headerLevel) {
        List<String> chunks = new ArrayList<>();
        
        // 先按段落标签切分
        String[] paragraphs = text.split("(?=<p>|<div>|<h[1-6]>|<section>|<article>)");
        
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            Matcher headerMatcher = HTML_HEADER_PATTERN.matcher(paragraph);
            if (headerMatcher.find()) {
                int level = Integer.parseInt(headerMatcher.group(1));
                if (currentChunk.length() > 0 && level <= headerLevel) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(paragraph);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 按 JSON 结构切分
     */
    private List<String> chunkJson(String text) {
        List<String> chunks = new ArrayList<>();
        
        try {
            // 简单处理：按顶级对象或数组元素切分
            text = text.trim();
            
            if (text.startsWith("[")) {
                // JSON 数组
                List<String> elements = parseJsonArray(text);
                for (String element : elements) {
                    if (!element.trim().isEmpty()) {
                        chunks.add(element.trim());
                    }
                }
            } else if (text.startsWith("{")) {
                // JSON 对象，按顶层字段切分
                chunks.add(text);
            } else {
                chunks.add(text);
            }
        } catch (Exception e) {
            // 解析失败，返回整个文本
            chunks.add(text);
        }

        return chunks;
    }

    /**
     * 解析 JSON 数组
     */
    private List<String> parseJsonArray(String text) {
        List<String> elements = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 1; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }

            if (c == ',' && depth == 0) {
                if (current.length() > 0) {
                    elements.add(current.toString().trim());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        // 添加最后一个元素
        if (current.length() > 0) {
            elements.add(current.toString().trim());
        }

        return elements;
    }

    /**
     * 按代码结构切分（简单实现：按函数或类切分）
     */
    private List<String> chunkCode(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 按代码块切分
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeBlockMatcher.find()) {
            String codeBlock = codeBlockMatcher.group(1);
            chunks.add(codeBlock.trim());
        }

        // 如果没有代码块，按段落切分
        if (chunks.isEmpty()) {
            return chunkByParagraph(text);
        }

        return chunks;
    }

    /**
     * 按段落切分（默认策略）
     */
    private List<String> chunkByParagraph(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }

        return chunks;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (config == null) {
            return false;
        }
        // 验证文档类型
        String docType = config.getDocumentType();
        return docType != null && !docType.isEmpty();
    }
}
