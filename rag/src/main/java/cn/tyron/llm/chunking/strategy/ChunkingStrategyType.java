package cn.tyron.llm.chunking.strategy;

/**
 * 分块策略枚举
 * 定义所有可用的文本分块策略类型
 */
public enum ChunkingStrategyType {
    /**
     * 固定大小分块
     */
    FIXED_SIZE("FIXED_SIZE", "固定大小分块"),

    /**
     * 重叠分块
     */
    OVERLAPPING("OVERLAPPING", "重叠分块"),

    /**
     * 递归分块（默认）
     */
    RECURSIVE("RECURSIVE", "递归分块"),

    /**
     * 语义分块
     */
    SEMANTIC("SEMANTIC", "语义分块"),

    /**
     * 滑动窗口分块
     */
    SLIDING_WINDOW("SLIDING_WINDOW", "滑动窗口分块"),

    /**
     * 文档结构分块
     */
    DOCUMENT_BASED("DOCUMENT_BASED", "文档结构分块"),

    /**
     * 智能体分块
     */
    AGENTIC("AGENTIC", "智能体分块"),

    /**
     * 混合分块
     */
    HYBRID("HYBRID", "混合分块"),

    /**
     * 上下文增强分块
     */
    CONTEXT_ENRICHED("CONTEXT_ENRICHED", "上下文增强分块"),

    /**
     * 子文档分块
     */
    SUB_DOCUMENT("SUB_DOCUMENT", "子文档分块");

    private final String name;
    private final String description;

    ChunkingStrategyType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据名称获取枚举值
     *
     * @param name 策略名称
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果名称不匹配
     */
    public static ChunkingStrategyType fromName(String name) {
        for (ChunkingStrategyType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown strategy name: " + name);
    }
}
