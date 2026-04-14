package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块策略
 * 按预设的字符数机械切分文本
 */
@Component
public class FixedSizeChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public String getStrategyName() {
        return "FIXED_SIZE";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int chunkSize = config.getChunkSize();
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize;
        }

        return chunks;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        return config.getChunkSize() > 0;
    }
}
