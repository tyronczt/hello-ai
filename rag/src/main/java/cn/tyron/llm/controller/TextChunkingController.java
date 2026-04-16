package cn.tyron.llm.controller;

import cn.tyron.llm.chunking.TextChunkingService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文本分块控制器
 */
@RestController
@RequestMapping("/api/chunking")
public class TextChunkingController {

    private final TextChunkingService textChunkingService;

    public TextChunkingController(TextChunkingService textChunkingService) {
        this.textChunkingService = textChunkingService;
    }

    /**
     * 固定大小分块接口
     */
    @GetMapping("/fixed")
    public Result<List<String>> fixedSizeChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize) {
        return Result.success(textChunkingService.fixedSizeChunk(text, chunkSize));
    }

    /**
     * 重叠分块接口
     */
    @GetMapping("/overlapping")
    public Result<List<String>> overlappingChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "50") int overlap) {
        return Result.success(textChunkingService.overlappingChunk(text, chunkSize, overlap));
    }

    /**
     * 递归分块
     */
    @GetMapping("/recursive")
    public Result<List<String>> recursiveChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int maxChunkSize) {
        return Result.success(textChunkingService.recursiveChunk(text, maxChunkSize));
    }

    /**
     * 滑动窗口分块
     */
    @GetMapping("/sliding-window")
    public Result<List<String>> slidingWindowChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int windowSize,
            @RequestParam(defaultValue = "100") int stride) {
        return Result.success(textChunkingService.slidingWindowChunk(text, windowSize, stride));
    }

    /**
     * 语义分块（基于 Embedding）
     */
    @GetMapping("/semantic")
    public Result<List<String>> semanticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "0.5") double similarityThreshold,
            @RequestParam(defaultValue = "500") int maxTokensPerChunk) {
        return Result.success(textChunkingService.semanticChunk(text, similarityThreshold, maxTokensPerChunk));
    }

    /**
     * 混合语义分块
     */
    @GetMapping("/hybrid")
    public Result<List<String>> hybridSemanticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "50") int chunkOverlap,
            @RequestParam(defaultValue = "0.75") double similarityThreshold) {
        return Result.success(textChunkingService.hybridSemanticChunk(text, chunkSize, chunkOverlap, similarityThreshold));
    }

    /**
     * Markdown 按标题分块（POST）
     */
    @PostMapping("/markdown")
    public Result<List<String>> markdownChunk(@RequestBody MarkdownRequest request) {
        return Result.success(textChunkingService.markdownChunkByHeader(request.getMarkdown(), request.getHeaderLevel()));
    }

    public static class MarkdownRequest {
        private String markdown;
        private int headerLevel = 2;

        public String getMarkdown() {
            return markdown;
        }

        public void setMarkdown(String markdown) {
            this.markdown = markdown;
        }

        public int getHeaderLevel() {
            return headerLevel;
        }

        public void setHeaderLevel(int headerLevel) {
            this.headerLevel = headerLevel;
        }
    }

    /**
     * Agentic 分块（按任务类型）
     */
    @GetMapping("/agentic")
    public Result<List<String>> agenticChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "semantic_search") String task) {
        return Result.success(textChunkingService.agenticChunk(text, task));
    }

    /**
     * 计算语义相似度
     */
    @GetMapping("/similarity")
    public Result<Double> calculateSimilarity(
            @RequestParam String text1,
            @RequestParam String text2) {
        return Result.success(textChunkingService.calculateSimilarity(text1, text2));
    }

    /**
     * 生成 Embedding 向量
     */
    @GetMapping("/embedding")
    public Result<List<Double>> generateEmbedding(@RequestParam String text) {
        return Result.success(textChunkingService.generateEmbedding(text));
    }

    /**
     * 上下文增强分块（返回带元数据的文档）
     */
    @GetMapping("/context-enriched")
    public Result<List<Map<String, Object>>> contextEnrichedChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "true") boolean enableEnrichment) {
        List<Document> docs = textChunkingService.contextEnrichedChunk(text, chunkSize, enableEnrichment);
        return Result.success(convertDocs(docs));
    }

    /**
     * 子文档分块（分层摘要，返回带元数据的文档）
     */
    @GetMapping("/sub-document")
    public Result<List<Map<String, Object>>> subDocumentChunk(
            @RequestParam String text,
            @RequestParam(defaultValue = "500") int chunkSize) {
        List<Document> docs = textChunkingService.subDocumentChunk(text, chunkSize);
        return Result.success(convertDocs(docs));
    }

    private List<Map<String, Object>> convertDocs(List<Document> docs) {
        return docs.stream().map(doc -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("text", doc.getText());
            map.put("metadata", doc.getMetadata());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 统一分块接口
     */
    @PostMapping("/chunk")
    public Result<List<String>> chunk(@RequestBody ChunkRequest request) {
        List<String> result;
        switch (request.getStrategy().toLowerCase()) {
            case "fixed":
                result = textChunkingService.fixedSizeChunk(request.getText(), request.getChunkSize());
                break;
            case "overlapping":
                result = textChunkingService.overlappingChunk(request.getText(), request.getChunkSize(), request.getOverlap());
                break;
            case "recursive":
                result = textChunkingService.recursiveChunk(request.getText(), request.getChunkSize());
                break;
            case "sliding_window":
                result = textChunkingService.slidingWindowChunk(request.getText(), request.getChunkSize(), request.getOverlap());
                break;
            case "semantic":
                result = textChunkingService.semanticChunk(request.getText(), request.getSimilarityThreshold(), request.getChunkSize());
                break;
            case "hybrid":
                result = textChunkingService.hybridSemanticChunk(request.getText(), request.getChunkSize(), request.getOverlap(), request.getSimilarityThreshold());
                break;
            case "agentic":
                result = textChunkingService.agenticChunk(request.getText(), request.getTask());
                break;
            default:
                result = textChunkingService.fixedSizeChunk(request.getText(), request.getChunkSize());
        }
        return Result.success(result);
    }

    // ========== 内部类 ==========

    public static class Result<T> {
        private int code;
        private String message;
        private T data;

        public static <T> Result<T> success(T data) {
            Result<T> result = new Result<>();
            result.code = 200;
            result.message = "success";
            result.data = data;
            return result;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    public static class ChunkRequest {
        private String text;
        private String strategy = "fixed";
        private int chunkSize = 500;
        private int overlap = 50;
        private double similarityThreshold = 0.5;
        private String task = "semantic_search";

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public String getTask() {
            return task;
        }

        public void setTask(String task) {
            this.task = task;
        }
    }
}
