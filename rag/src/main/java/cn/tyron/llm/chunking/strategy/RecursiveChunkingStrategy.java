package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 递归分块策略
 * 参考 com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter 实现
 *
 * 核心思想：
 * 1. 按优先级顺序使用分隔符列表进行切分（段落 → 句子 → 短语 → 字符）
 * 2. 先用高优先级分隔符切分，若片段仍过大则递归使用下一级分隔符
 * 3. 合并小片段避免产生过多碎片
 * 4. 支持重叠以保持上下文连贯性
 */
@Component
public class RecursiveChunkingStrategy extends AbstractChunkingStrategy {

    // 默认分隔符列表，按优先级从粗到细排列
    private static final List<String> DEFAULT_SEPARATORS = Arrays.asList(
            "\n\n\n",   // 三连换行（章节分隔）
            "\n\n",    // 双换行（段落分隔）
            "\n",      // 单换行
            "。\n",     // 中文句号+换行
            "！",       // 中文感叹号
            "？",       // 中文问号
            "；",       // 中文分号
            "。",       // 中文句号
            ".\n",      // 英文句号+换行
            "; ",       // 英文分号+空格
            ", ",       // 英文逗号+空格
            "，",       // 中文逗号
            ".",        // 英文句号
            " ",        // 空格
            ""          // 空字符串（字符级别兜底）
    );

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.RECURSIVE;
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int chunkSize = config.getChunkSize();
        int overlap = Math.min(config.getChunkOverlap(), chunkSize / 2);
        List<String> separators = config.getSeparators() != null ?
                config.getSeparators() : DEFAULT_SEPARATORS;

        // 递归切分
        List<String> chunks = recursiveSplit(text, chunkSize, separators, 0);

        // 应用重叠处理
        if (overlap > 0) {
            chunks = applyOverlap(chunks, overlap);
        }

        // 过滤空白和过短片段
        return chunks.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 递归切分文本
     *
     * @param text 待切分文本
     * @param maxChunkSize 块大小上限
     * @param separators 可用的分隔符列表
     * @param separatorIndex 当前使用的分隔符索引
     */
    private List<String> recursiveSplit(String text, int maxChunkSize,
                                         List<String> separators,
                                         int separatorIndex) {
        List<String> result = new ArrayList<>();

        // 文本已符合要求，直接返回
        if (text.length() <= maxChunkSize) {
            result.add(text);
            return result;
        }

        // 已用完所有分隔符，强制硬切分
        if (separatorIndex >= separators.size()) {
            return hardSplit(text, maxChunkSize);
        }

        String separator = separators.get(separatorIndex);
        Pattern pattern = Pattern.compile(Pattern.quote(separator), Pattern.LITERAL);

        // 用当前分隔符切分
        String[] splits = pattern.split(text);

        // 如果分隔符无法切分（只有一个结果），尝试下一级
        if (splits.length <= 1) {
            return recursiveSplit(text, maxChunkSize, separators, separatorIndex + 1);
        }

        // 合并小片段
        List<String> merged = mergeSplits(splits, separator, maxChunkSize);

        // 检查每个片段，对超长片段递归使用下一级分隔符切分
        for (String chunk : merged) {
            if (chunk.length() > maxChunkSize) {
                result.addAll(recursiveSplit(chunk, maxChunkSize, separators, separatorIndex + 1));
            } else {
                result.add(chunk);
            }
        }

        return result;
    }

    /**
     * 合并相邻的小片段，避免产生过多碎片
     */
    private List<String> mergeSplits(String[] splits, String separator, int maxSize) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < splits.length; i++) {
            String part = splits[i];

            if (current.length() > 0 && !separator.isEmpty()) {
                current.append(separator);
            }
            current.append(part);

            // 当前合并后的大小接近上限时，保存当前块并开始新块
            if (current.length() >= maxSize * 0.9) {
                String merged = current.toString().trim();
                if (!merged.isEmpty()) {
                    result.add(merged);
                }
                current.setLength(0);
            }
        }

        // 处理剩余内容
        if (current.length() > 0) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                result.add(remaining);
            }
        }

        return result.isEmpty() ? Arrays.asList(splits) : result;
    }

    /**
     * 强制硬切分（所有分隔符都无法有效切分时的兜底方案）
     */
    private List<String> hardSplit(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            start = end;
        }

        return result;
    }

    /**
     * 应用重叠处理，保持上下文连贯性
     */
    private List<String> applyOverlap(List<String> chunks, int overlap) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<String> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String current = chunks.get(i);

            if (i == 0) {
                result.add(current);
                continue;
            }

            // 获取前一个块的末尾作为前缀
            String previous = chunks.get(i - 1);
            int prefixLength = Math.min(overlap, previous.length());
            String prefix = previous.substring(previous.length() - prefixLength);

            // 合并前缀和当前块
            result.add(prefix + current);
        }

        return result;
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        return config.getChunkSize() > 0 && config.getChunkOverlap() < config.getChunkSize();
    }
}
