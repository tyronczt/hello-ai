package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 语义分块策略
 * 计算句子 Embedding 的相似度，在主题切换处（相似度下降）切分
 */
@Component
public class SemanticChunkingStrategy extends AbstractChunkingStrategy {

    private final EmbeddingModel embeddingModel;

    public SemanticChunkingStrategy(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String getStrategyName() {
        return "SEMANTIC";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        double similarityThreshold = config.getSimilarityThreshold();
        int maxTokensPerChunk = config.getMaxTokensPerChunk();

        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceTokens = estimateTokenCount(sentence);

            // 如果当前块已满，先保存
            if (currentTokenCount + sentenceTokens > maxTokensPerChunk && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentTokenCount = 0;
            }

            // 检查语义相似度，决定是否切分
            if (currentChunk.length() > 0 && i > 0) {
                double similarity = calculateSentenceSimilarity(sentences.get(i - 1), sentence);
                if (similarity < similarityThreshold) {
                    // 语义发生明显变化，在这里切分
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    currentTokenCount = 0;
                }
            }

            // 添加句子到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            currentTokenCount += sentenceTokens;
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 计算两个句子的语义相似度
     */
    private double calculateSentenceSimilarity(String sentence1, String sentence2) {
        try {
            float[] embedding1 = embeddingModel.embed(sentence1);
            float[] embedding2 = embeddingModel.embed(sentence2);

            if (embedding1 == null || embedding2 == null || 
                embedding1.length == 0 || embedding2.length == 0) {
                return calculateKeywordSimilarity(sentence1, sentence2);
            }

            return cosineSimilarity(embedding1, embedding2);
        } catch (Exception e) {
            // 如果 Embedding 失败，使用关键词相似度作为降级方案
            return calculateKeywordSimilarity(sentence1, sentence2);
        }
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        // 相似度阈值应在 0-1 之间
        return config.getSimilarityThreshold() >= 0 && config.getSimilarityThreshold() <= 1;
    }
}
