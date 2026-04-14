package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 分块策略工厂
 * 用于管理和获取各种分块策略实例
 */
@Component
public class ChunkingStrategyFactory {

    private final Map<String, ChunkingStrategy> strategies = new HashMap<>();

    public ChunkingStrategyFactory(
            FixedSizeChunkingStrategy fixedSizeStrategy,
            OverlappingChunkingStrategy overlappingStrategy,
            RecursiveChunkingStrategy recursiveStrategy,
            TokenChunkingStrategy tokenStrategy,
            SemanticChunkingStrategy semanticStrategy,
            SlidingWindowChunkingStrategy slidingWindowStrategy,
            DocumentBasedChunkingStrategy documentBasedStrategy,
            AgenticChunkingStrategy agenticStrategy,
            HybridChunkingStrategy hybridStrategy,
            ContextEnrichedChunkingStrategy contextEnrichedStrategy,
            SubDocumentChunkingStrategy subDocumentStrategy) {

        // 注册所有策略
        registerStrategy(fixedSizeStrategy);
        registerStrategy(overlappingStrategy);
        registerStrategy(recursiveStrategy);
        registerStrategy(tokenStrategy);
        registerStrategy(semanticStrategy);
        registerStrategy(slidingWindowStrategy);
        registerStrategy(documentBasedStrategy);
        registerStrategy(agenticStrategy);
        registerStrategy(hybridStrategy);
        registerStrategy(contextEnrichedStrategy);
        registerStrategy(subDocumentStrategy);
    }

    /**
     * 注册策略
     */
    private void registerStrategy(ChunkingStrategy strategy) {
        strategies.put(strategy.getStrategyName(), strategy);
    }

    /**
     * 根据策略名称获取策略实例
     *
     * @param strategyName 策略名称
     * @return 分块策略实例
     * @throws IllegalArgumentException 如果策略不存在
     */
    public ChunkingStrategy getStrategy(String strategyName) {
        ChunkingStrategy strategy = strategies.get(strategyName.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + strategyName +
                    ". Available strategies: " + getAvailableStrategies());
        }
        return strategy;
    }

    /**
     * 获取默认策略（递归分块）
     *
     * @return 默认分块策略
     */
    public ChunkingStrategy getDefaultStrategy() {
        return strategies.get("RECURSIVE");
    }

    /**
     * 获取所有可用的策略名称
     *
     * @return 策略名称列表
     */
    public String getAvailableStrategies() {
        return String.join(", ", strategies.keySet());
    }

    /**
     * 检查策略是否存在
     *
     * @param strategyName 策略名称
     * @return 是否存在
     */
    public boolean hasStrategy(String strategyName) {
        return strategies.containsKey(strategyName.toUpperCase());
    }

    /**
     * 策略类型枚举
     */
    public enum StrategyType {
        FIXED_SIZE("固定大小分块"),
        OVERLAPPING("重叠分块"),
        RECURSIVE("递归分块"),
        TOKEN("Token分块"),
        SEMANTIC("语义分块"),
        SLIDING_WINDOW("滑动窗口分块"),
        DOCUMENT_BASED("文档结构分块"),
        AGENTIC("智能体分块"),
        HYBRID("混合分块"),
        CONTEXT_ENRICHED("上下文增强分块"),
        SUB_DOCUMENT("子文档分块");

        private final String description;

        StrategyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
