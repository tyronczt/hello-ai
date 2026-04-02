package cn.tyron.llm.pdfexport.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FoConvertService {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的 XSL-FO 模板生成专家。请根据用户提供的 PDF 文本内容，生成符合 Apache FOP 规范的.fo 文件内容。
    
            ## 核心任务：
            分析 PDF 文档的结构和内容，生成 XSL-FO 模板。对于文档中的具体业务数据（如会议名称、时间、人员等），
            使用 ${fieldName} 占位符替换，而不是保留具体内容。这样后续可以用实际数据填充这些占位符。
    
            ## 输出要求：
            1. 直接输出.fo 文件内容，不要有任何解释、代码块标记或其他文字
            2. 业务数据必须使用 ${fieldName} 占位符表示，例如 ${meetingName}、${meetingTime}
            3. 占位符名称应该是英文，清晰表达字段含义
            4. 确保输出的是完整的 XSL-FO 文档结构
            5. 对于会议记录类文档，识别以下字段并用占位符替换：
               - 会议名称 → ${meetingName}
               - 会议时间 → ${meetingTime}
               - 会议地点 → ${meetingLocation}
               - 主持人 → ${host}
               - 记录人 → ${recorder}
    
            ## XSL-FO 基础模板结构：
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
            - 使用中文字体时，font-family 设置为 "Microsoft YaHei", "SimSun", "STSong-Light"
            - 示例：<fo:block font-family="Microsoft YaHei">中文内容</fo:block>
    
            ## 常见 XSL-FO 元素：
            - fo:block: 块级元素（段落）
            - fo:inline: 行内元素
            - fo:table/fo:table-row/fo:table-cell: 表格
            - fo:page-number: 页码
            - fo:leader: 填充线
    
            ## 处理示例：
            如果 PDF 内容是：
            ```
            第一季度产品规划复盘会
            会议时间：2026 年 4 月 1 日 14:00 - 15:30
            会议地点：会议室 A
            主持人：张三
            记录人：李四
            ```
                
            你应该生成：
            ```xml
            <fo:block font-size="18pt" font-weight="bold" text-align="center" space-after="12pt">
              ${meetingName}
            </fo:block>
            <fo:table>
              <fo:table-row>
                <fo:table-cell><fo:block>会议时间：</fo:block></fo:table-cell>
                <fo:table-cell><fo:block>${meetingTime}</fo:block></fo:table-cell>
              </fo:table-row>
              <fo:table-row>
                <fo:table-cell><fo:block>会议地点：</fo:block></fo:table-cell>
                <fo:table-cell><fo:block>${meetingLocation}</fo:block></fo:table-cell>
              </fo:table-row>
              <fo:table-row>
                <fo:table-cell><fo:block>主持人：</fo:block></fo:table-cell>
                <fo:table-cell><fo:block>${host}</fo:block></fo:table-cell>
              </fo:table-row>
              <fo:table-row>
                <fo:table-cell><fo:block>记录人：</fo:block></fo:table-cell>
                <fo:table-cell><fo:block>${recorder}</fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table>
            ```
    
            请仔细分析 PDF 内容，保持原有布局结构，将具体的业务数据用占位符替换。
            """;

    /**
     * 将 PDF 文本内容转换为 XSL-FO 模板
     *
     * @param pdfText PDF 中提取的文本内容
     * @return .fo 模板内容
     */
    public String convertToFoTemplate(String pdfText) {
        log.info("========== 开始调用 AI 转换 PDF 内容为.fo 模板 ==========");
        
        try {
            // 记录 PDF 内容长度
            log.info("步骤 1/3: PDF 文本内容长度：{} 字符", pdfText.length());
            
            // 创建智能体
            log.info("步骤 2/3: 创建 ReActAgent 智能体...");
            ReActAgent agent = ReActAgent.builder()
                    .name("FoTemplateGenerator")
                    .sysPrompt(SYSTEM_PROMPT)
                    .model(OpenAIChatModel.builder()
                            .baseUrl(baseUrl)
                            .apiKey(apiKey)
                            .modelName(modelName)
                            .build())
                    .build();
            
            // 发送消息
            log.info("步骤 3/3: 调用 AI 模型进行转换...");
            Msg msg = Msg.builder()
                    .textContent(pdfText)
                    .build();
            
            Msg response = agent.call(msg).block();
            String result = response.getTextContent();
            
            log.info("AI 模型响应完成，原始结果长度：{} 字符", result.length());
            
            // 清理可能的代码块标记
            log.info("清理结果格式...");
            result = cleanFoContent(result);
            
            log.info("========== AI 转换完成 ==========\n生成的.fo 模板长度：{} 字符", result.length());
            return result;
            
        } catch (Exception e) {
            log.error("❌ AI 转换失败：{}", e.getMessage(), e);
            throw new RuntimeException("AI 转换失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清理.fo 内容中的代码块标记
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
