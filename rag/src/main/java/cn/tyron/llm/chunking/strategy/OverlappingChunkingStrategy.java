package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 重叠分块策略
 * 相邻块之间保留一定比例的重叠内容，保持上下文连贯
 */
@Component
public class OverlappingChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.OVERLAPPING;
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int chunkSize = config.getChunkSize();
        int overlap = config.getChunkOverlap();

        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }

        int step = chunkSize - overlap;
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += step;
        }

        return chunks;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        return config.getChunkOverlap() >= 0 && config.getChunkOverlap() < config.getChunkSize();
    }
}
