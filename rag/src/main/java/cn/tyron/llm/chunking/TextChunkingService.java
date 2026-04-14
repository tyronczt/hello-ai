package cn.tyron.llm.chunking;

import cn.tyron.llm.chunking.strategy.ChunkingStrategy;
import cn.tyron.llm.chunking.strategy.ChunkingStrategy.ChunkingConfig;
import cn.tyron.llm.chunking.strategy.ChunkingStrategyFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本分块服务
 * 使用策略模式支持多种文本分块策略
 * 
 * 支持的分块策略：
 * - FIXED_SIZE: 固定大小分块
 * - OVERLAPPING: 重叠分块
 * - RECURSIVE: 递归分块（默认）
 * - TOKEN: Token级别分块
 * - SEMANTIC: 语义分块
 * - SLIDING_WINDOW: 滑动窗口分块
 * - DOCUMENT_BASED: 文档结构分块
 * - AGENTIC: 智能体分块
 * - HYBRID: 混合分块
 * - CONTEXT_ENRICHED: 上下文增强分块
 * - SUB_DOCUMENT: 子文档分块
 */
@Service
public class TextChunkingService {

    private final ChunkingStrategyFactory strategyFactory;
    private final EmbeddingModel embeddingModel;

    public TextChunkingService(ChunkingStrategyFactory strategyFactory, 
                               EmbeddingModel embeddingModel) {
        this.strategyFactory = strategyFactory;
        this.embeddingModel = embeddingModel;
    }

    // ==================== 通用分块方法 ====================

    /**
     * 使用指定策略进行分块
     *
     * @param text 待分块文本
     * @param strategyName 策略名称
     * @param config 分块配置
     * @return 分块后的文本列表
     */
    public List<String> chunk(String text, String strategyName, ChunkingConfig config) {
        ChunkingStrategy strategy = strategyFactory.getStrategy(strategyName);
        return strategy.chunk(text, config);
    }

    /**
     * 使用指定策略进行分块（返回 Document 对象）
     *
     * @param text 待分块文本
     * @param strategyName 策略名称
     * @param config 分块配置
     * @return 分块后的 Document 列表
     */
    public List<Document> chunkToDocuments(String text, String strategyName, ChunkingConfig config) {
        ChunkingStrategy strategy = strategyFactory.getStrategy(strategyName);
        return strategy.chunkToDocuments(text, config);
    }

    /**
     * 使用默认策略（递归分块）进行分块
     *
     * @param text 待分块文本
     * @param config 分块配置
     * @return 分块后的文本列表
     */
    public List<String> chunk(String text, ChunkingConfig config) {
        return chunk(text, "RECURSIVE", config);
    }

    // ==================== 便捷方法：固定大小分块 ====================

    /**
     * 固定大小分块
     *
     * @param text 待分块文本
     * @param chunkSize 块大小
     * @return 分块后的文本列表
     */
    public List<String> fixedSizeChunk(String text, int chunkSize) {
        ChunkingConfig config = ChunkingConfig.fixedSizeConfig(chunkSize, 0);
        return chunk(text, "FIXED_SIZE", config);
    }

    /**
     * 固定大小分块（带重叠）
     *
     * @param text 待分块文本
     * @param chunkSize 块大小
     * @param overlap 重叠大小
     * @return 分块后的文本列表
     */
    public List<String> fixedSizeChunk(String text, int chunkSize, int overlap) {
        ChunkingConfig config = ChunkingConfig.fixedSizeConfig(chunkSize, overlap);
        return chunk(text, "OVERLAPPING", config);
    }

    // ==================== 便捷方法：递归分块 ====================

    /**
     * 递归分块
     *
     * @param text 待分块文本
     * @param maxChunkSize 最大块大小
     * @return 分块后的文本列表
     */
    public List<String> recursiveChunk(String text, int maxChunkSize) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(maxChunkSize);
        return chunk(text, "RECURSIVE", config);
    }

    // ==================== 便捷方法：Token 分块 ====================

    /**
     * Token 级别分块
     *
     * @param text 待分块文本
     * @param chunkSize Token 数量
     * @return 分块后的文本列表
     */
    public List<String> tokenChunk(String text, int chunkSize) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(chunkSize);
        return chunk(text, "TOKEN", config);
    }

    /**
     * Token 级别分块（带重叠）
     *
     * @param text 待分块文本
     * @param chunkSize Token 数量
     * @param chunkOverlap 重叠 Token 数量
     * @return 分块后的文本列表
     */
    public List<String> tokenChunk(String text, int chunkSize, int chunkOverlap) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(chunkSize);
        config.setChunkOverlap(chunkOverlap);
        return chunk(text, "TOKEN", config);
    }

    // ==================== 便捷方法：语义分块 ====================

    /**
     * 语义分块
     *
     * @param text 待分块文本
     * @param similarityThreshold 相似度阈值
     * @param maxTokensPerChunk 每块最大 Token 数
     * @return 分块后的文本列表
     */
    public List<String> semanticChunk(String text, double similarityThreshold, int maxTokensPerChunk) {
        ChunkingConfig config = ChunkingConfig.semanticConfig(similarityThreshold, maxTokensPerChunk);
        return chunk(text, "SEMANTIC", config);
    }

    // ==================== 便捷方法：滑动窗口分块 ====================

    /**
     * 滑动窗口分块
     *
     * @param text 待分块文本
     * @param windowSize 窗口大小
     * @param stride 滑动步长
     * @return 分块后的文本列表
     */
    public List<String> slidingWindowChunk(String text, int windowSize, int stride) {
        ChunkingConfig config = ChunkingConfig.slidingWindowConfig(windowSize, stride);
        return chunk(text, "SLIDING_WINDOW", config);
    }

    // ==================== 便捷方法：文档结构分块 ====================

    /**
     * Markdown 文档按标题分块
     *
     * @param markdown Markdown 文本
     * @param headerLevel 标题层级
     * @return 分块后的文本列表
     */
    public List<String> markdownChunkByHeader(String markdown, int headerLevel) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setDocumentType("markdown");
        config.setHeaderLevel(headerLevel);
        return chunk(markdown, "DOCUMENT_BASED", config);
    }

    /**
     * HTML 文档按结构分块
     *
     * @param html HTML 文本
     * @param headerLevel 标题层级
     * @return 分块后的文本列表
     */
    public List<String> htmlChunkByHeader(String html, int headerLevel) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setDocumentType("html");
        config.setHeaderLevel(headerLevel);
        return chunk(html, "DOCUMENT_BASED", config);
    }

    // ==================== 便捷方法：智能体分块 ====================

    /**
     * 智能体分块（根据任务类型自动选择策略）
     *
     * @param text 待分块文本
     * @param taskType 任务类型（summarization, qa, semantic_search, code_analysis）
     * @return 分块后的文本列表
     */
    public List<String> agenticChunk(String text, String taskType) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setTaskType(taskType);
        return chunk(text, "AGENTIC", config);
    }

    // ==================== 便捷方法：混合分块 ====================

    /**
     * 混合分块（Token + 语义）
     *
     * @param text 待分块文本
     * @param chunkSize 块大小
     * @param similarityThreshold 相似度阈值
     * @return 分块后的文本列表
     */
    public List<String> hybridChunk(String text, int chunkSize, double similarityThreshold) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(chunkSize);
        config.setSimilarityThreshold(similarityThreshold);
        config.setHybridStrategies(List.of("TOKEN_SEMANTIC"));
        return chunk(text, "HYBRID", config);
    }

    // ==================== 便捷方法：上下文增强分块 ====================

    /**
     * 上下文增强分块
     *
     * @param text 待分块文本
     * @param chunkSize 块大小
     * @param enableEnrichment 是否启用上下文增强
     * @return 分块后的 Document 列表
     */
    public List<Document> contextEnrichedChunk(String text, int chunkSize, boolean enableEnrichment) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(chunkSize);
        config.setEnableContextEnrichment(enableEnrichment);
        return chunkToDocuments(text, "CONTEXT_ENRICHED", config);
    }

    // ==================== 便捷方法：子文档分块 ====================

    /**
     * 子文档分块（分层摘要）
     *
     * @param text 待分块文本
     * @param chunkSize 块大小
     * @return 分块后的 Document 列表
     */
    public List<Document> subDocumentChunk(String text, int chunkSize) {
        ChunkingConfig config = ChunkingConfig.defaultConfig();
        config.setChunkSize(chunkSize);
        return chunkToDocuments(text, "SUB_DOCUMENT", config);
    }

    // ==================== 工具方法 ====================

    /**
     * 获取所有可用的分块策略
     *
     * @return 策略名称列表
     */
    public String getAvailableStrategies() {
        return strategyFactory.getAvailableStrategies();
    }

    /**
     * 估算文本的 Token 数量
     *
     * @param text 文本
     * @return Token 数量估算值
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long otherChars = text.length() - chineseChars;
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }

    /**
     * 生成文本的 Embedding 向量
     *
     * @param text 文本
     * @return Embedding 向量
     */
    public List<Double> generateEmbedding(String text) {
        try {
            float[] embedding = embeddingModel.embed(text);
            List<Double> result = new java.util.ArrayList<>(embedding.length);
            for (float v : embedding) {
                result.add((double) v);
            }
            return result;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 计算两个文本的余弦相似度
     *
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度值（0-1）
     */
    public double calculateSimilarity(String text1, String text2) {
        try {
            float[] embed1 = embeddingModel.embed(text1);
            float[] embed2 = embeddingModel.embed(text2);
            return cosineSimilarity(embed1, embed2);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
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
}
