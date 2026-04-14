package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口分块策略
 * 通过滑动窗口创建重叠块，窗口每次滑动固定步长
 */
@Component
public class SlidingWindowChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public String getStrategyName() {
        return "SLIDING_WINDOW";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int windowSize = config.getWindowSize();
        int stride = config.getStride();

        if (stride <= 0) {
            throw new IllegalArgumentException("stride must be greater than 0");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be greater than 0");
        }
        if (stride > windowSize) {
            throw new IllegalArgumentException("stride should not be greater than windowSize");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + windowSize, text.length());
            chunks.add(text.substring(start, end));
            
            // 滑动窗口
            start += stride;
            
            // 如果剩余文本不足一个窗口且已经创建过块，则结束
            if (end == text.length()) {
                break;
            }
        }

        return chunks;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (config == null) {
            return false;
        }
        return config.getWindowSize() > 0 && 
               config.getStride() > 0 && 
               config.getStride() <= config.getWindowSize();
    }
}
