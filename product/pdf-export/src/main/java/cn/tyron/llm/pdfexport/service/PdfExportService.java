package cn.tyron.llm.pdfexport.service;

import cn.tyron.llm.pdfexport.config.FopConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.springframework.stereotype.Service;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final FopConfig fopConfig;

    private FopFactory getFopFactory() {
        return fopConfig.getFopFactory();
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 填充.fo模板中的占位符
     *
     * @param foTemplate .fo模板内容
     * @param data 填充数据
     * @return 填充后的.fo内容
     */
    public String fillTemplate(String foTemplate, Map<String, Object> data) {
        String filled = foTemplate;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            Object value = entry.getValue();
            String replacement = value != null ? value.toString() : "";
            filled = filled.replace(placeholder, escapeXml(replacement));
        }
        
        return filled;
    }

    /**
     * 从.fo模板中提取所有占位符
     *
     * @param foTemplate .fo模板内容
     * @return 占位符名称列表
     */
    public java.util.List<String> extractPlaceholders(String foTemplate) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(foTemplate);
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        
        return placeholders;
    }

    /**
     * 使用FOP将.fo模板渲染为PDF
     *
     * @param foContent 填充后的.fo内容
     * @return PDF字节数组
     */
    public byte[] renderToPdf(String foContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FopFactory factory = getFopFactory();
            FOUserAgent foUserAgent = factory.newFOUserAgent();
            
            Fop fop = factory.newFop(MimeConstants.MIME_PDF, foUserAgent, outputStream);
            
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            
            Source source = new StreamSource(new StringReader(foContent));
            Result result = new StreamResult(outputStream);
            
            transformer.transform(source, result);
            
            log.info("PDF渲染成功，大小: {} bytes", outputStream.size());
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("PDF渲染失败", e);
            throw new RuntimeException("PDF渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充数据并渲染为PDF
     *
     * @param foTemplate .fo模板内容
     * @param data 填充数据
     * @return PDF字节数组
     */
    public byte[] fillAndRender(String foTemplate, Map<String, Object> data) {
        String filledFo = fillTemplate(foTemplate, data);
        return renderToPdf(filledFo);
    }

    /**
     * XML转义
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
