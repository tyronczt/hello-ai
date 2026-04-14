package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 分块策略抽象基类
 * 提供通用的辅助方法和默认实现
 */
public abstract class AbstractChunkingStrategy implements ChunkingStrategy {

    @Override
    public List<Document> chunkToDocuments(String text, ChunkingConfig config) {
        List<String> chunks = chunk(text, config);
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chunk_index", i);
            metadata.put("chunk_total", chunks.size());
            metadata.put("strategy", getStrategyName());
            metadata.put("chunk_id", UUID.randomUUID().toString());
            
            // 添加自定义元数据
            if (config.getMetadata() != null) {
                metadata.putAll(config.getMetadata());
            }
            
            documents.add(new Document(chunk, metadata));
        }
        
        return documents;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (config == null) {
            return false;
        }
        // 基础验证
        if (config.getChunkSize() <= 0) {
            return false;
        }
        if (config.getChunkOverlap() < 0 || config.getChunkOverlap() >= config.getChunkSize()) {
            return false;
        }
        return true;
    }

    /**
     * 估算文本的 token 数量
     * 中文按 1.5 字符/token，英文按 4 字符/token 估算
     */
    protected int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long otherChars = text.length() - chineseChars;
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }

    /**
     * 将文本分割成句子
     */
    protected List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("[。！？.!?\\n]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    protected double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
    }

    /**
     * 基于关键词计算相似度（降级方案）
     */
    protected double calculateKeywordSimilarity(String text1, String text2) {
        java.util.Set<String> words1 = new java.util.HashSet<>(java.util.Arrays.asList(text1.split("\\s+")));
        java.util.Set<String> words2 = new java.util.HashSet<>(java.util.Arrays.asList(text2.split("\\s+")));
        java.util.Set<String> intersection = new java.util.HashSet<>(words1);
        intersection.retainAll(words2);
        java.util.Set<String> union = new java.util.HashSet<>(words1);
        union.addAll(words2);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    /**
     * 提取文本末尾的指定 token 数量的内容作为重叠部分
     */
    protected String extractOverlap(String text, int overlapTokens) {
        int charEstimate = overlapTokens * 4;
        if (text.length() <= charEstimate) return text;
        return text.substring(Math.max(0, text.length() - charEstimate));
    }
}
