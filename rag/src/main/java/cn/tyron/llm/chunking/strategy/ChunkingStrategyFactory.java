package cn.tyron.llm.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 分块策略工厂
 * 用于管理和获取各种分块策略实例
 */
@Component
public class ChunkingStrategyFactory {

    private final Map<ChunkingStrategyType, ChunkingStrategy> strategies = new EnumMap<>(ChunkingStrategyType.class);

    public ChunkingStrategyFactory(
            FixedSizeChunkingStrategy fixedSizeStrategy,
            OverlappingChunkingStrategy overlappingStrategy,
            RecursiveChunkingStrategy recursiveStrategy,
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
        strategies.put(strategy.getStrategyType(), strategy);
    }

    /**
     * 根据策略类型获取策略实例
     *
     * @param strategyType 策略类型
     * @return 分块策略实例
     * @throws IllegalArgumentException 如果策略不存在
     */
    public ChunkingStrategy getStrategy(ChunkingStrategyType strategyType) {
        ChunkingStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + strategyType +
                    ". Available strategies: " + getAvailableStrategies());
        }
        return strategy;
    }

    /**
     * 根据策略名称获取策略实例
     *
     * @param strategyName 策略名称
     * @return 分块策略实例
     * @throws IllegalArgumentException 如果策略不存在
     */
    public ChunkingStrategy getStrategy(String strategyName) {
        return getStrategy(ChunkingStrategyType.fromName(strategyName));
    }

    /**
     * 获取默认策略（递归分块）
     *
     * @return 默认分块策略
     */
    public ChunkingStrategy getDefaultStrategy() {
        return strategies.get(ChunkingStrategyType.RECURSIVE);
    }

    /**
     * 获取所有可用的策略名称
     *
     * @return 策略名称列表
     */
    public String getAvailableStrategies() {
        return String.join(", ", strategies.keySet().stream()
                .map(ChunkingStrategyType::getName)
                .toList());
    }

    /**
     * 检查策略是否存在
     *
     * @param strategyType 策略类型
     * @return 是否存在
     */
    public boolean hasStrategy(ChunkingStrategyType strategyType) {
        return strategies.containsKey(strategyType);
    }
}
