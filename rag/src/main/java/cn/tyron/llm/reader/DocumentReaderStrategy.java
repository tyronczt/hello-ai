package cn.tyron.llm.reader;

import org.springframework.ai.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @description: 文档读取策略
 * @author: chenzt
 * @create: 2026-04-10
 **/
public interface DocumentReaderStrategy {
    /**
     * 判断是否支持该文件
     */
    boolean supports(File file);

    /**
     * 读取文件并返回 Document 列表
     */
    List<Document> read(File file) throws IOException;
}
