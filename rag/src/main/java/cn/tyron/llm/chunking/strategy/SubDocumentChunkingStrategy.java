package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子文档分块策略 (Sub-document Chunking)
 * 先总结整个文档或大章节，将摘要作为元数据附加到各个子块
 */
@Component
public class SubDocumentChunkingStrategy extends AbstractChunkingStrategy {

    private final RecursiveChunkingStrategy recursiveStrategy;
    private final DocumentBasedChunkingStrategy documentBasedStrategy;

    public SubDocumentChunkingStrategy(RecursiveChunkingStrategy recursiveStrategy,
                                       DocumentBasedChunkingStrategy documentBasedStrategy) {
        this.recursiveStrategy = recursiveStrategy;
        this.documentBasedStrategy = documentBasedStrategy;
    }

    @Override
    public ChunkingStrategyType getStrategyType() {
        return ChunkingStrategyType.SUB_DOCUMENT;
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        // 第一步：按文档结构切分大章节
        List<String> sections = splitIntoSections(text, config);
        
        // 第二步：对每个章节进行分块
        List<String> allChunks = new ArrayList<>();
        
        for (String section : sections) {
            List<String> sectionChunks = recursiveStrategy.chunk(section, config);
            allChunks.addAll(sectionChunks);
        }

        return allChunks;
    }

    @Override
    public List<Document> chunkToDocuments(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        // 第一层：生成文档级摘要
        String documentSummary = generateDocumentSummary(text);

        // 第二层：按章节切分
        List<SectionInfo> sections = extractSections(text, config);
        
        List<Document> allDocuments = new ArrayList<>();
        
        for (SectionInfo section : sections) {
            // 生成章节摘要
            String sectionSummary = generateSectionSummary(section.content);
            
            // 第三层：对章节进行分块
            ChunkingConfig sectionConfig = ChunkingConfig.defaultConfig();
            sectionConfig.setChunkSize(config.getChunkSize());
            sectionConfig.setChunkOverlap(config.getChunkOverlap());
            
            List<String> chunks = recursiveStrategy.chunk(section.content, sectionConfig);
            
            // 为每个块添加分层摘要
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("chunk_index", i);
                metadata.put("chunk_total", chunks.size());
                metadata.put("section_title", section.title);
                metadata.put("section_index", section.index);
                metadata.put("strategy", getStrategyName());
                
                // 分层摘要
                metadata.put("document_summary", documentSummary);
                metadata.put("section_summary", sectionSummary);
                
                // 添加上下文信息
                if (i > 0) {
                    metadata.put("has_previous", true);
                }
                if (i < chunks.size() - 1) {
                    metadata.put("has_next", true);
                }

                // 构建增强内容
                String enrichedContent = buildHierarchicalContent(
                    chunk, 
                    documentSummary, 
                    sectionSummary, 
                    section.title
                );
                
                allDocuments.add(new Document(enrichedContent, metadata));
            }
        }

        return allDocuments;
    }

    /**
     * 将文本切分为大章节
     */
    private List<String> splitIntoSections(String text, ChunkingConfig config) {
        // 根据文档类型选择切分方式
        if ("markdown".equalsIgnoreCase(config.getDocumentType())) {
            ChunkingConfig mdConfig = ChunkingConfig.defaultConfig();
            mdConfig.setDocumentType("markdown");
            mdConfig.setHeaderLevel(1); // 按一级标题切分
            return documentBasedStrategy.chunk(text, mdConfig);
        }
        
        // 默认按段落切分大段
        List<String> sections = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");
        
        StringBuilder currentSection = new StringBuilder();
        int currentSize = 0;
        int sectionSize = config.getChunkSize() * 5; // 大章节大小
        
        for (String paragraph : paragraphs) {
            if (currentSize + paragraph.length() > sectionSize && currentSection.length() > 0) {
                sections.add(currentSection.toString().trim());
                currentSection = new StringBuilder();
                currentSize = 0;
            }
            currentSection.append(paragraph).append("\n\n");
            currentSize += paragraph.length();
        }
        
        if (currentSection.length() > 0) {
            sections.add(currentSection.toString().trim());
        }
        
        return sections;
    }

    /**
     * 提取章节信息
     */
    private List<SectionInfo> extractSections(String text, ChunkingConfig config) {
        List<SectionInfo> sections = new ArrayList<>();
        
        List<String> sectionTexts = splitIntoSections(text, config);
        
        for (int i = 0; i < sectionTexts.size(); i++) {
            String sectionText = sectionTexts.get(i);
            String title = extractSectionTitle(sectionText);
            
            sections.add(new SectionInfo(i, title, sectionText));
        }
        
        return sections;
    }

    /**
     * 提取章节标题
     */
    private String extractSectionTitle(String sectionText) {
        String[] lines = sectionText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) {
                return line.replaceAll("^#+\\s*", "").trim();
            }
            if (line.matches("^第[一二三四五六七八九十\\d]+章.*")) {
                return line;
            }
        }
        return "Section";
    }

    /**
     * 生成文档级摘要
     * 实际项目中可以使用 LLM 生成
     */
    private String generateDocumentSummary(String text) {
        // 简化实现：取开头和结尾
        int previewLength = 150;
        String beginning = text.substring(0, Math.min(text.length(), previewLength));
        String summary = "文档主题：" + beginning;
        if (text.length() > previewLength * 2) {
            String ending = text.substring(text.length() - previewLength);
            summary += "... 文档结尾：" + ending;
        }
        return summary;
    }

    /**
     * 生成章节摘要
     */
    private String generateSectionSummary(String sectionText) {
        // 简化实现：取前 100 个字符
        int length = Math.min(sectionText.length(), 100);
        return sectionText.substring(0, length) + "...";
    }

    /**
     * 构建分层内容
     */
    private String buildHierarchicalContent(String chunk, String docSummary, 
                                            String sectionSummary, String sectionTitle) {
        StringBuilder content = new StringBuilder();
        
        content.append("【文档摘要】").append(docSummary).append("\n");
        content.append("【章节：").append(sectionTitle).append("】").append(sectionSummary).append("\n");
        content.append("【内容】\n").append(chunk);
        
        return content.toString();
    }

    /**
     * 章节信息内部类
     */
    private static class SectionInfo {
        final int index;
        final String title;
        final String content;

        SectionInfo(int index, String title, String content) {
            this.index = index;
            this.title = title;
            this.content = content;
        }
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        return config != null && 
               recursiveStrategy.validateConfig(config) &&
               config.getChunkSize() > 0;
    }
}
