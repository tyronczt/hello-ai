package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 递归分块策略
 * 按优先级顺序（段落→句子→空格→字符）递归切分，直至块大小符合要求
 */
@Component
public class RecursiveChunkingStrategy extends AbstractChunkingStrategy {

    // 默认分隔符列表，按优先级排序
    private static final List<String> DEFAULT_SEPARATORS = Arrays.asList(
            "\n\n",      // 段落
            "\n",        // 换行
            "。",        // 中文句号
            ". ",        // 英文句号+空格
            "；",        // 中文分号
            "; ",        // 英文分号+空格
            "，",        // 中文逗号
            ", ",        // 英文逗号+空格
            " "          // 空格
    );

    @Override
    public String getStrategyName() {
        return "RECURSIVE";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int maxChunkSize = config.getChunkSize();
        List<String> separators = config.getSeparators() != null ? 
                config.getSeparators() : DEFAULT_SEPARATORS;

        return recursiveSplit(text, maxChunkSize, separators);
    }

    /**
     * 递归切分文本
     */
    private List<String> recursiveSplit(String text, int maxChunkSize, List<String> separators) {
        List<String> result = new ArrayList<>();

        // 如果文本长度符合要求，直接返回
        if (text.length() <= maxChunkSize) {
            result.add(text);
            return result;
        }

        // 寻找最佳切分点
        String bestSeparator = null;
        int bestIndex = -1;

        for (String sep : separators) {
            int index = text.lastIndexOf(sep, maxChunkSize);
            if (index > 0) {
                bestSeparator = sep;
                bestIndex = index;
                break;
            }
        }

        if (bestSeparator == null) {
            // 没有找到合适的分隔符，强制按字符切分
            result.add(text.substring(0, maxChunkSize));
            result.addAll(recursiveSplit(text.substring(maxChunkSize), maxChunkSize, separators));
        } else {
            // 按找到的分隔符切分
            String first = text.substring(0, bestIndex + bestSeparator.length());
            String remaining = text.substring(bestIndex + bestSeparator.length());
            result.add(first.trim());
            result.addAll(recursiveSplit(remaining, maxChunkSize, separators));
        }

        return result;
    }
}
