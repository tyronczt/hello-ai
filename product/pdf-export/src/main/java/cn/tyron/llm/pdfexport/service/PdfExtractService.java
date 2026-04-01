package cn.tyron.llm.pdfexport.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class PdfExtractService {

    /**
     * 从PDF中提取文本内容
     *
     * @param inputStream PDF文件的输入流
     * @return 提取的文本内容
     */
    public String extractText(InputStream inputStream) {
        StringBuilder text = new StringBuilder();
        
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            int pageCount = document.getNumberOfPages();
            log.info("PDF总页数: {}", pageCount);
            
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                text.append("=== 第 ").append(i).append(" 页 ===\n")
                    .append(pageText)
                    .append("\n");
            }
            
        } catch (Exception e) {
            log.error("PDF文本提取失败", e);
            throw new RuntimeException("PDF文本提取失败: " + e.getMessage(), e);
        }
        
        return text.toString();
    }

    /**
     * 从PDF中提取指定页码范围的文本
     *
     * @param inputStream PDF文件的输入流
     * @param startPage 起始页码（从1开始）
     * @param endPage 结束页码
     * @return 提取的文本内容
     */
    public String extractText(InputStream inputStream, int startPage, int endPage) {
        StringBuilder text = new StringBuilder();
        
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            
            text.append(stripper.getText(document));
            
        } catch (Exception e) {
            log.error("PDF文本提取失败", e);
            throw new RuntimeException("PDF文本提取失败: " + e.getMessage(), e);
        }
        
        return text.toString();
    }
}
