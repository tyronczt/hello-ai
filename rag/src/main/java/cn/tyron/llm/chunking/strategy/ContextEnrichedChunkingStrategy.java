package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文增强分块策略 (Context-Enriched Chunking)
 * 为每个块添加周围块的摘要或元数据，保持序列间的上下文
 */
@Component
public class ContextEnrichedChunkingStrategy extends AbstractChunkingStrategy {

    private final RecursiveChunkingStrategy recursiveStrategy;

    public ContextEnrichedChunkingStrategy(RecursiveChunkingStrategy recursiveStrategy) {
        this.recursiveStrategy = recursiveStrategy;
    }

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.CONTEXT_ENRICHED;
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        // 先进行基础分块
        List<String> baseChunks = recursiveStrategy.chunk(text, config);
        
        if (baseChunks.size() <= 1) {
            return baseChunks;
        }

        // 添加上下文增强
        return enrichWithContext(baseChunks, config);
    }

    @Override
    public List<Document> chunkToDocuments(String text, ChunkingConfig config) {
        List<String> baseChunks = recursiveStrategy.chunk(text, config);
        
        if (baseChunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Document> documents = new ArrayList<>();
        int totalChunks = baseChunks.size();

        // 生成文档级摘要（简化实现）
        String documentSummary = generateSummary(text, 200);

        for (int i = 0; i < totalChunks; i++) {
            String chunk = baseChunks.get(i);
            
            // 构建增强的元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chunk_index", i);
            metadata.put("chunk_total", totalChunks);
            metadata.put("strategy", getStrategyName());
            
            // 添加文档级上下文
            metadata.put("document_summary", documentSummary);
            
            // 添加相邻块信息
            if (i > 0) {
                String prevSummary = generateSummary(baseChunks.get(i - 1), 50);
                metadata.put("previous_chunk_summary", prevSummary);
            }
            if (i < totalChunks - 1) {
                String nextSummary = generateSummary(baseChunks.get(i + 1), 50);
                metadata.put("next_chunk_summary", nextSummary);
            }

            // 添加章节信息（如果可用）
            String sectionInfo = detectSection(chunk);
            if (sectionInfo != null) {
                metadata.put("section", sectionInfo);
            }

            // 添加用户自定义元数据
            if (config.getMetadata() != null) {
                metadata.putAll(config.getMetadata());
            }

            // 构建增强的文本内容
            String enrichedContent = buildEnrichedContent(chunk, metadata);
            
            documents.add(new Document(enrichedContent, metadata));
        }

        return documents;
    }

    /**
     * 为文本块列表添加上下文信息
     */
    private List<String> enrichWithContext(List<String> chunks, ChunkingConfig config) {
        List<String> enrichedChunks = new ArrayList<>();
        int totalChunks = chunks.size();

        for (int i = 0; i < totalChunks; i++) {
            StringBuilder enriched = new StringBuilder();
            
            // 添加文档上下文前缀
            enriched.append("[块 ").append(i + 1).append("/").append(totalChunks).append("]\n");
            
            // 添加上下文信息
            if (config.isEnableContextEnrichment()) {
                if (i > 0) {
                    enriched.append("【上文】").append(generateSummary(chunks.get(i - 1), 30)).append("\n");
                }
                
                enriched.append("【当前内容】\n");
                enriched.append(chunks.get(i));
                
                if (i < totalChunks - 1) {
                    enriched.append("\n【下文】").append(generateSummary(chunks.get(i + 1), 30));
                }
            } else {
                enriched.append(chunks.get(i));
            }

            enrichedChunks.add(enriched.toString());
        }

        return enrichedChunks;
    }

    /**
     * 构建增强的内容字符串
     */
    private String buildEnrichedContent(String chunk, Map<String, Object> metadata) {
        StringBuilder enriched = new StringBuilder();
        
        // 添加元数据前缀
        if (metadata.containsKey("section")) {
            enriched.append("【章节】").append(metadata.get("section")).append("\n");
        }
        
        if (metadata.containsKey("previous_chunk_summary")) {
            enriched.append("【上文】").append(metadata.get("previous_chunk_summary")).append("\n");
        }
        
        enriched.append("【内容】\n").append(chunk);
        
        if (metadata.containsKey("next_chunk_summary")) {
            enriched.append("\n【下文】").append(metadata.get("next_chunk_summary"));
        }

        return enriched.toString();
    }

    /**
     * 生成文本摘要（简化实现）
     * 实际项目中可以使用 LLM 生成更好的摘要
     */
    private String generateSummary(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 简单实现：取前 maxLength 个字符
        String summary = text.substring(0, Math.min(text.length(), maxLength));
        if (text.length() > maxLength) {
            summary += "...";
        }
        return summary;
    }

    /**
     * 检测文本所属的章节
     */
    private String detectSection(String text) {
        // 简单实现：检测 Markdown 标题或常见章节标记
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) {
                return line.replaceAll("^#+\\s*", "").trim();
            }
            if (line.matches("^第[一二三四五六七八九十\\d]+章.*")) {
                return line;
            }
            if (line.matches("^\\d+\\.\\s+.*")) {
                return line;
            }
        }
        return null;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        return config != null && recursiveStrategy.validateConfig(config);
    }
}
