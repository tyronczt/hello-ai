package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 混合分块策略 (Hybrid Chunking)
 * 结合多种分块策略，如 Token 分块 + 语义相似度合并
 */
@Component
public class HybridChunkingStrategy extends AbstractChunkingStrategy {

    private final EmbeddingModel embeddingModel;
    private final TokenChunkingStrategy tokenStrategy;
    private final RecursiveChunkingStrategy recursiveStrategy;
    private final SemanticChunkingStrategy semanticStrategy;

    public HybridChunkingStrategy(EmbeddingModel embeddingModel,
                                  TokenChunkingStrategy tokenStrategy,
                                  RecursiveChunkingStrategy recursiveStrategy,
                                  SemanticChunkingStrategy semanticStrategy) {
        this.embeddingModel = embeddingModel;
        this.tokenStrategy = tokenStrategy;
        this.recursiveStrategy = recursiveStrategy;
        this.semanticStrategy = semanticStrategy;
    }

    @Override
    public String getStrategyName() {
        return "HYBRID";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> hybridStrategies = config.getHybridStrategies();
        if (hybridStrategies == null || hybridStrategies.isEmpty()) {
            // 默认使用 "token + semantic" 组合
            hybridStrategies = List.of("TOKEN_SEMANTIC");
        }

        String primaryStrategy = hybridStrategies.get(0);
        
        return switch (primaryStrategy.toUpperCase()) {
            case "TOKEN_SEMANTIC" -> tokenSemanticHybrid(text, config);
            case "RECURSIVE_SEMANTIC" -> recursiveSemanticHybrid(text, config);
            case "STRUCTURE_SEMANTIC" -> structureSemanticHybrid(text, config);
            case "SLIDING_MERGE" -> slidingWindowMerge(text, config);
            default -> tokenSemanticHybrid(text, config);
        };
    }

    /**
     * Token 分块 + 语义合并
     * 先用 Token 分块，然后对语义相似的相邻块进行合并
     */
    private List<String> tokenSemanticHybrid(String text, ChunkingConfig config) {
        // 第一步：Token 分块（较小的块）
        ChunkingConfig tokenConfig = ChunkingConfig.defaultConfig();
        tokenConfig.setChunkSize(config.getChunkSize() / 2);
        tokenConfig.setChunkOverlap(config.getChunkOverlap() / 2);
        
        List<String> initialChunks = tokenStrategy.chunk(text, tokenConfig);
        
        if (initialChunks.size() <= 1) {
            return initialChunks;
        }

        // 第二步：基于语义相似度合并
        return mergeBySemanticSimilarity(initialChunks, config);
    }

    /**
     * 递归分块 + 语义边界检查
     * 先用递归分块，然后检查边界处的语义相似度
     */
    private List<String> recursiveSemanticHybrid(String text, ChunkingConfig config) {
        // 第一步：递归分块
        List<String> initialChunks = recursiveStrategy.chunk(text, config);
        
        if (initialChunks.size() <= 1) {
            return initialChunks;
        }

        // 第二步：检查边界并可能合并
        List<String> refinedChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(initialChunks.get(0));

        for (int i = 1; i < initialChunks.size(); i++) {
            String nextChunk = initialChunks.get(i);
            
            // 检查当前块末尾和下一个块开头的语义相似度
            double similarity = calculateBoundarySimilarity(
                currentChunk.toString(), 
                nextChunk
            );

            if (similarity >= config.getSimilarityThreshold() && 
                estimateTokenCount(currentChunk.toString() + nextChunk) <= config.getChunkSize() * 1.2) {
                // 语义相似且合并后不超过限制，则合并
                currentChunk.append(" ").append(nextChunk);
            } else {
                // 不合并，保存当前块
                refinedChunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(nextChunk);
            }
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            refinedChunks.add(currentChunk.toString().trim());
        }

        return refinedChunks;
    }

    /**
     * 文档结构 + 语义分块
     * 先按文档结构切分大段，再对每个大段进行语义分块
     */
    private List<String> structureSemanticHybrid(String text, ChunkingConfig config) {
        // 第一步：按段落/结构切分大段
        ChunkingConfig structureConfig = ChunkingConfig.defaultConfig();
        structureConfig.setChunkSize(config.getChunkSize() * 2); // 更大的初始块
        
        List<String> largeChunks = recursiveStrategy.chunk(text, structureConfig);
        
        // 第二步：对每个大段进行语义分块
        List<String> finalChunks = new ArrayList<>();
        ChunkingConfig semanticConfig = ChunkingConfig.semanticConfig(
            config.getSimilarityThreshold(), 
            config.getChunkSize()
        );

        for (String largeChunk : largeChunks) {
            if (estimateTokenCount(largeChunk) <= config.getChunkSize()) {
                finalChunks.add(largeChunk);
            } else {
                List<String> semanticChunks = semanticStrategy.chunk(largeChunk, semanticConfig);
                finalChunks.addAll(semanticChunks);
            }
        }

        return finalChunks;
    }

    /**
     * 滑动窗口 + 智能合并
     * 使用滑动窗口创建重叠块，然后智能合并相似块
     */
    private List<String> slidingWindowMerge(String text, ChunkingConfig config) {
        // 第一步：滑动窗口分块
        ChunkingConfig slidingConfig = ChunkingConfig.slidingWindowConfig(
            config.getWindowSize(), 
            config.getStride()
        );
        
        SlidingWindowChunkingStrategy slidingStrategy = new SlidingWindowChunkingStrategy();
        List<String> windowChunks = slidingStrategy.chunk(text, slidingConfig);
        
        if (windowChunks.size() <= 1) {
            return windowChunks;
        }

        // 第二步：去重和合并相似块
        return deduplicateAndMerge(windowChunks, config);
    }

    /**
     * 基于语义相似度合并块
     */
    private List<String> mergeBySemanticSimilarity(List<String> chunks, ChunkingConfig config) {
        List<String> merged = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(chunks.get(0));
        int currentTokens = estimateTokenCount(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String nextChunk = chunks.get(i);
            int nextTokens = estimateTokenCount(nextChunk);

            if (currentTokens + nextTokens <= config.getChunkSize()) {
                // 检查语义相似度
                double similarity = calculateBoundarySimilarity(
                    currentChunk.toString(), 
                    nextChunk
                );

                if (similarity >= config.getSimilarityThreshold()) {
                    // 合并
                    currentChunk.append(" ").append(nextChunk);
                    currentTokens += nextTokens;
                    continue;
                }
            }

            // 不合并，保存当前块
            merged.add(currentChunk.toString().trim());
            currentChunk = new StringBuilder(nextChunk);
            currentTokens = nextTokens;
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            merged.add(currentChunk.toString().trim());
        }

        return merged;
    }

    /**
     * 去重并合并相似块
     */
    private List<String> deduplicateAndMerge(List<String> chunks, ChunkingConfig config) {
        List<String> result = new ArrayList<>();
        
        for (String chunk : chunks) {
            boolean isDuplicate = false;
            
            for (String existing : result) {
                double similarity = calculateChunkSimilarity(chunk, existing);
                if (similarity > 0.9) { // 高度相似认为是重复
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                result.add(chunk);
            }
        }

        return result;
    }

    /**
     * 计算两个块边界的语义相似度
     */
    private double calculateBoundarySimilarity(String chunk1, String chunk2) {
        try {
            // 提取 chunk1 的后半部分和 chunk2 的前半部分
            String tail1 = chunk1.substring(Math.max(0, chunk1.length() - 100));
            String head2 = chunk2.substring(0, Math.min(chunk2.length(), 100));

            float[] embed1 = embeddingModel.embed(tail1);
            float[] embed2 = embeddingModel.embed(head2);

            return cosineSimilarity(embed1, embed2);
        } catch (Exception e) {
            // 降级到关键词相似度
            return calculateKeywordSimilarity(chunk1, chunk2);
        }
    }

    /**
     * 计算两个块的整体相似度
     */
    private double calculateChunkSimilarity(String chunk1, String chunk2) {
        try {
            float[] embed1 = embeddingModel.embed(chunk1);
            float[] embed2 = embeddingModel.embed(chunk2);
            return cosineSimilarity(embed1, embed2);
        } catch (Exception e) {
            return calculateKeywordSimilarity(chunk1, chunk2);
        }
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        return config.getSimilarityThreshold() >= 0 && config.getSimilarityThreshold() <= 1;
    }
}
