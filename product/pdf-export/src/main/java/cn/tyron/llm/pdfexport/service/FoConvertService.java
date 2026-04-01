package cn.tyron.llm.pdfexport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoConvertService {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的XSL-FO模板生成专家。请根据用户提供的PDF文本内容，生成符合Apache FOP规范的.fo文件内容。

            ## 输出要求：
            1. 直接输出.fo文件内容，不要有任何解释、markdown代码块标记或其他文字
            2. 业务数据使用 ${fieldName} 占位符表示，例如 ${name}、${date}、${amount}
            3. 占位符名称应该是英文，清晰表达字段含义
            4. 确保输出的是完整的XSL-FO文档结构

            ## XSL-FO基础模板结构：
            ```xml
            <?xml version="1.0" encoding="UTF-8"?>
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <fo:layout-master-set>
                <fo:simple-page-master master-name="main"
                      page-width="210mm" page-height="297mm"
                      margin-top="20mm" margin-bottom="20mm"
                      margin-left="25mm" margin-right="25mm">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="main">
                <fo:flow flow-name="xsl-region-body">
                  <!-- 内容区域 -->
                </fo:flow>
              </fo:page-sequence>
            </fo:root>
            ```

            ## 中文支持：
            - 使用中文字体时，font-family设置为 "Microsoft YaHei", "SimSun", "STSong-Light"
            - 示例：<fo:block font-family="Microsoft YaHei">中文内容</fo:block>

            ## 常见XSL-FO元素：
            - fo:block: 块级元素（段落）
            - fo:inline: 行内元素
            - fo:table/fo:table-row/fo:table-cell: 表格
            - fo:page-number: 页码
            - fo:leader: 填充线

            请仔细分析PDF内容，保持原有布局结构，用占位符替换需要填充的业务数据。
            """;

    /**
     * 将PDF文本内容转换为XSL-FO模板
     *
     * @param pdfText PDF中提取的文本内容
     * @return .fo模板内容
     */
    public String convertToFoTemplate(String pdfText) {
        log.info("开始调用AI转换PDF内容为.fo模板...");
        
        try {
            PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("pdfContent", pdfText));
            
            String result = chatClient.prompt(prompt)
                    .call()
                    .content();
            
            // 清理可能的markdown代码块标记
            result = cleanFoContent(result);
            
            log.info("AI转换完成，生成的.fo模板长度: {}", result.length());
            return result;
            
        } catch (Exception e) {
            log.error("AI转换失败", e);
            throw new RuntimeException("AI转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理.fo内容中的markdown标记
     */
    private String cleanFoContent(String content) {
        // 移除开头的 ```xml 或 ``` 和结尾的 ```
        String cleaned = content.trim();
        if (cleaned.startsWith("```xml")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
