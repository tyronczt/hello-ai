package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块策略
 * 按预设的字符数切分文本，支持重叠
 */
@Component
public class FixedSizeChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.FIXED_SIZE;
    }

    @Override
    /**
     * 按固定大小对文本进行分块处理
     * 将输入文本按照配置的块大小切分为多个子字符串片段，不支持重叠
     *
     * @param text 待分块的原始文本内容
     * @param config 分块配置对象，包含块大小(chunkSize)等参数
     * @return 分块后的文本列表，如果输入文本为空则返回空列表
     */
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();
        int chunkSize = config.getChunkSize();

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
