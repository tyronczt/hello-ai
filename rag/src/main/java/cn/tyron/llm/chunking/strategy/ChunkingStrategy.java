package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * 文本分块策略接口
 * 定义所有分块策略的通用契约
 */
public interface ChunkingStrategy {

    /**
     * 获取策略名称
     */
    String getStrategyName();

    /**
     * 执行分块操作
     *
     * @param text 待分块的文本
     * @param config 分块配置参数
     * @return 分块后的文本列表
     */
    List<String> chunk(String text, ChunkingConfig config);

    /**
     * 执行分块操作（返回 Spring AI Document）
     *
     * @param text 待分块的文本
     * @param config 分块配置参数
     * @return 分块后的 Document 列表
     */
    List<Document> chunkToDocuments(String text, ChunkingConfig config);

    /**
     * 验证配置是否有效
     *
     * @param config 分块配置参数
     * @return 是否有效
     */
    boolean validateConfig(ChunkingConfig config);

    /**
     * 分块配置类
     */
    class ChunkingConfig {
        // 基础参数
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        
        // 滑动窗口参数
        private int windowSize = 500;
        private int stride = 250;
        
        // 语义分块参数
        private double similarityThreshold = 0.75;
        private int maxTokensPerChunk = 500;
        
        // 递归分块参数
        private List<String> separators;
        
        // 文档结构参数
        private String documentType = "text"; // text, markdown, html, json
        private int headerLevel = 2; // 对于 markdown/html，按几级标题切分
        
        // Agentic 参数
        private String taskType = "default"; // summarization, qa, semantic_search
        
        // 上下文增强参数
        private boolean enableContextEnrichment = false;
        private Map<String, String> metadata;
        
        // 混合策略参数
        private List<String> hybridStrategies;

        // Getters and Setters
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

        public int getWindowSize() { return windowSize; }
        public void setWindowSize(int windowSize) { this.windowSize = windowSize; }

        public int getStride() { return stride; }
        public void setStride(int stride) { this.stride = stride; }

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

        public int getMaxTokensPerChunk() { return maxTokensPerChunk; }
        public void setMaxTokensPerChunk(int maxTokensPerChunk) { this.maxTokensPerChunk = maxTokensPerChunk; }

        public List<String> getSeparators() { return separators; }
        public void setSeparators(List<String> separators) { this.separators = separators; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public int getHeaderLevel() { return headerLevel; }
        public void setHeaderLevel(int headerLevel) { this.headerLevel = headerLevel; }

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }

        public boolean isEnableContextEnrichment() { return enableContextEnrichment; }
        public void setEnableContextEnrichment(boolean enableContextEnrichment) { this.enableContextEnrichment = enableContextEnrichment; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

        public List<String> getHybridStrategies() { return hybridStrategies; }
        public void setHybridStrategies(List<String> hybridStrategies) { this.hybridStrategies = hybridStrategies; }

        /**
         * 创建默认配置
         */
        public static ChunkingConfig defaultConfig() {
            return new ChunkingConfig();
        }

        /**
         * 创建固定大小分块配置
         */
        public static ChunkingConfig fixedSizeConfig(int chunkSize, int overlap) {
            ChunkingConfig config = new ChunkingConfig();
            config.setChunkSize(chunkSize);
            config.setChunkOverlap(overlap);
            return config;
        }

        /**
         * 创建滑动窗口配置
         */
        public static ChunkingConfig slidingWindowConfig(int windowSize, int stride) {
            ChunkingConfig config = new ChunkingConfig();
            config.setWindowSize(windowSize);
            config.setStride(stride);
            return config;
        }

        /**
         * 创建语义分块配置
         */
        public static ChunkingConfig semanticConfig(double threshold, int maxTokens) {
            ChunkingConfig config = new ChunkingConfig();
            config.setSimilarityThreshold(threshold);
            config.setMaxTokensPerChunk(maxTokens);
            return config;
        }
    }
}
