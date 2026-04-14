package cn.tyron.llm.chunking.strategy;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Token 级别分块策略
 * 使用 Spring AI 的 TokenTextSplitter 进行基于 Token 的分块
 */
@Component
public class TokenChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public String getStrategyName() {
        return "TOKEN";
    }

    @Override
    public List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        int chunkSize = config.getChunkSize();
        int chunkOverlap = config.getChunkOverlap();

        // 使用 Spring AI 的 TokenTextSplitter
        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,           // defaultChunkSize
                chunkOverlap,        // defaultMinChunkSize
                chunkOverlap,        // defaultMinChunkLengthChars
                chunkSize * 2,       // defaultMaxChunkLengthChars
                true                 // keepDelimiter
        );

        Document document = new Document(text);
        List<Document> splitDocs = splitter.split(Collections.singletonList(document));

        return splitDocs.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    @Override
    public boolean validateConfig(ChunkingConfig config) {
        if (!super.validateConfig(config)) {
            return false;
        }
        // Token 分块需要合理的 chunkSize，通常 100-2000 之间
        return config.getChunkSize() >= 50 && config.getChunkSize() <= 4000;
    }
}
