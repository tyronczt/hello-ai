package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能体分块策略 (Agentic / LLM-based Chunking)
 * 由 Agent 根据任务（如总结、问答）动态决定如何切分
 */
@Component
public class AgenticChunkingStrategy extends AbstractChunkingStrategy {

    private final ChatClient chatClient;
    private final List<ChunkingStrategy> fallbackStrategies;

    public AgenticChunkingStrategy(ChatClient chatClient,
                                   TokenChunkingStrategy tokenStrategy,
                                   SemanticChunkingStrategy semanticStrategy,
                                   RecursiveChunkingStrategy recursiveStrategy) {
        this.chatClient = chatClient;
        this.fallbackStrategies = List.of(tokenStrategy, semanticStrategy, recursiveStrategy);
    }

    @Override
    public String getStrategyName() {
        return "AGENTIC";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        String taskType = config.getTaskType();

        // 根据任务类型选择策略
        return switch (taskType.toLowerCase()) {
            case "summarization", "summary" -> chunkForSummarization(text, config);
            case "qa", "question_answering" -> chunkForQA(text, config);
            case "semantic_search", "search" -> chunkForSemanticSearch(text, config);
            case "code_analysis", "code" -> chunkForCodeAnalysis(text, config);
            case "llm_decision" -> chunkByLLMDecision(text, config);
            default -> chunkWithDefaultStrategy(text, config);
        };
    }

    /**
     * 为总结任务分块 - 使用较大的块
     */
    private List<String> chunkForSummarization(String text, ChunkingConfig config) {
        // 总结任务需要更多上下文，使用较大的块
        ChunkingConfig summaryConfig = ChunkingConfig.defaultConfig();
        summaryConfig.setChunkSize(800);
        summaryConfig.setChunkOverlap(100);
        
        // 使用 Token 分块策略
        return fallbackStrategies.get(0).chunk(text, summaryConfig);
    }

    /**
     * 为问答任务分块 - 使用较小的块，确保精确匹配
     */
    private List<String> chunkForQA(String text, ChunkingConfig config) {
        // QA 任务需要精确匹配，使用较小的块
        ChunkingConfig qaConfig = ChunkingConfig.defaultConfig();
        qaConfig.setChunkSize(300);
        qaConfig.setChunkOverlap(50);
        
        return fallbackStrategies.get(0).chunk(text, qaConfig);
    }

    /**
     * 为语义搜索分块 - 使用语义分块
     */
    private List<String> chunkForSemanticSearch(String text, ChunkingConfig config) {
        // 语义搜索使用语义分块
        ChunkingConfig searchConfig = ChunkingConfig.semanticConfig(0.75, 500);
        return fallbackStrategies.get(1).chunk(text, searchConfig);
    }

    /**
     * 为代码分析分块 - 按代码结构切分
     */
    private List<String> chunkForCodeAnalysis(String text, ChunkingConfig config) {
        // 代码分析使用递归分块，优先保留代码结构
        ChunkingConfig codeConfig = ChunkingConfig.defaultConfig();
        codeConfig.setChunkSize(500);
        codeConfig.setSeparators(List.of("\n\n", "\n", ";", "}"));
        
        return fallbackStrategies.get(2).chunk(text, codeConfig);
    }

    /**
     * 由 LLM 决定如何分块
     */
    private List<String> chunkByLLMDecision(String text, ChunkingConfig config) {
        if (chatClient == null) {
            // 如果没有 ChatClient，使用默认策略
            return chunkWithDefaultStrategy(text, config);
        }

        try {
            // 构建提示词，让 LLM 分析文本并建议分块策略
            String promptTemplate = """
                请分析以下文本的结构和内容，决定最佳的分块策略。
                
                文本内容：
                {text}
                
                请回答以下问题：
                1. 这是什么类型的文本？（技术文档、故事、代码、对话等）
                2. 建议的块大小（字符数）：
                3. 是否需要重叠？如果需要，建议的重叠大小：
                4. 应该按什么边界切分？（段落、句子、函数、主题等）
                5. 推荐的切分位置（列出具体的切分点描述）：
                
                请以结构化的方式回答。
                """;

            PromptTemplate template = new PromptTemplate(promptTemplate);
            Prompt prompt = template.create(Map.of("text", text.substring(0, Math.min(text.length(), 2000))));
            
            String response = chatClient.prompt(prompt).call().content();
            
            // 解析 LLM 的响应，提取分块建议
            // 这里简化处理，实际可以根据响应调整策略
            int suggestedChunkSize = extractChunkSizeFromResponse(response);
            
            ChunkingConfig llmConfig = ChunkingConfig.defaultConfig();
            llmConfig.setChunkSize(suggestedChunkSize > 0 ? suggestedChunkSize : 500);
            
            return fallbackStrategies.get(0).chunk(text, llmConfig);
            
        } catch (Exception e) {
            // LLM 调用失败，使用默认策略
            return chunkWithDefaultStrategy(text, config);
        }
    }

    /**
     * 从 LLM 响应中提取建议的块大小
     */
    private int extractChunkSizeFromResponse(String response) {
        try {
            // 简单解析：查找数字
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{2,4})\\s*字符");
            java.util.regex.Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            // 解析失败返回 -1
        }
        return -1;
    }

    /**
     * 使用默认策略分块
     */
    private List<String> chunkWithDefaultStrategy(String text, ChunkingConfig config) {
        // 默认使用递归分块
        return fallbackStrategies.get(2).chunk(text, config);
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        return config != null && config.getTaskType() != null;
    }
}
