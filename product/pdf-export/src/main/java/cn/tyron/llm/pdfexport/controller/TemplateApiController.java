package cn.tyron.llm.pdfexport.controller;

import cn.tyron.llm.pdfexport.entity.PdfRecord;
import cn.tyron.llm.pdfexport.entity.PdfTemplate;
import cn.tyron.llm.pdfexport.mapper.PdfRecordMapper;
import cn.tyron.llm.pdfexport.mapper.PdfTemplateMapper;
import cn.tyron.llm.pdfexport.service.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateApiController {

    private final PdfTemplateMapper templateMapper;
    private final PdfRecordMapper recordMapper;
    private final MinioService minioService;
    private final PdfExtractService pdfExtractService;
    private final FoConvertService foConvertService;
    private final PdfExportService pdfExportService;

    /**
     * 上传PDF模板
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "name", required = false) String name,
                                     @RequestParam(value = "description", required = false) String description) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.contains("pdf")) {
                result.put("success", false);
                result.put("message", "只支持上传PDF文件");
                return result;
            }
            
            // 上传到MinIO
            String objectKey = minioService.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    "application/pdf"
            );
            
            // 保存到数据库
            PdfTemplate template = new PdfTemplate();
            template.setName(name != null && !name.isEmpty() ? name : file.getOriginalFilename());
            template.setDescription(description);
            template.setPdfObjectKey(objectKey);
            template.setStatus("UPLOADED");
            
            templateMapper.insert(template);
            
            result.put("success", true);
            result.put("message", "上传成功");
            result.put("data", Map.of(
                    "id", template.getId(),
                    "name", template.getName()
            ));
            
        } catch (Exception e) {
            log.error("上传失败", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            PdfTemplate template = templateMapper.selectById(id);
            if (template == null) {
                result.put("success", false);
                result.put("message", "模板不存在");
                return result;
            }
            
            // 删除MinIO中的文件
            if (template.getPdfObjectKey() != null) {
                try {
                    minioService.deleteFile(template.getPdfObjectKey());
                } catch (Exception e) {
                    log.warn("删除MinIO文件失败", e);
                }
            }
            
            // 删除数据库记录（会级联删除关联记录）
            templateMapper.deleteById(id);
            
            result.put("success", true);
            result.put("message", "删除成功");
            
        } catch (Exception e) {
            log.error("删除失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 转换PDF为.fo模板
     */
    @PostMapping("/{id}/convert")
    public Map<String, Object> convert(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            PdfTemplate template = templateMapper.selectById(id);
            if (template == null) {
                result.put("success", false);
                result.put("message", "模板不存在");
                return result;
            }
            
            // 更新状态为转换中
            templateMapper.update(null, new LambdaUpdateWrapper<PdfTemplate>()
                    .eq(PdfTemplate::getId, id)
                    .set(PdfTemplate::getStatus, "CONVERTING")
                    .set(PdfTemplate::getErrorMsg, null));
            
            // 1. 从MinIO下载PDF
            InputStream pdfStream = minioService.downloadFile(template.getPdfObjectKey());
            
            // 2. 使用PDFBox提取文本
            String pdfText = pdfExtractService.extractText(pdfStream);
            
            if (pdfText == null || pdfText.trim().isEmpty()) {
                throw new RuntimeException("无法从PDF中提取文本内容");
            }
            
            // 3. 调用AI转换为.fo模板
            String foContent = foConvertService.convertToFoTemplate(pdfText);
            
            // 4. 保存.fo内容到数据库
            templateMapper.update(null, new LambdaUpdateWrapper<PdfTemplate>()
                    .eq(PdfTemplate::getId, id)
                    .set(PdfTemplate::getFoContent, foContent)
                    .set(PdfTemplate::getStatus, "CONVERTED"));
            
            result.put("success", true);
            result.put("message", "转换成功");
            result.put("data", Map.of("foContent", foContent));
            
        } catch (Exception e) {
            log.error("转换失败", e);
            templateMapper.update(null, new LambdaUpdateWrapper<PdfTemplate>()
                    .eq(PdfTemplate::getId, id)
                    .set(PdfTemplate::getStatus, "ERROR")
                    .set(PdfTemplate::getErrorMsg, e.getMessage()));
            
            result.put("success", false);
            result.put("message", "转换失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 保存.fo模板内容
     */
    @PutMapping("/{id}/fo")
    public Map<String, Object> saveFo(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String foContent = body.get("foContent");
            if (foContent == null || foContent.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "fo内容不能为空");
                return result;
            }
            
            templateMapper.update(null, new LambdaUpdateWrapper<PdfTemplate>()
                    .eq(PdfTemplate::getId, id)
                    .set(PdfTemplate::getFoContent, foContent)
                    .set(PdfTemplate::getStatus, "CONVERTED"));
            
            result.put("success", true);
            result.put("message", "保存成功");
            
        } catch (Exception e) {
            log.error("保存失败", e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取占位符列表
     */
    @GetMapping("/{id}/placeholders")
    public Map<String, Object> getPlaceholders(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            PdfTemplate template = templateMapper.selectById(id);
            if (template == null || template.getFoContent() == null) {
                result.put("success", false);
                result.put("message", "模板不存在或未转换");
                return result;
            }
            
            List<String> placeholders = pdfExportService.extractPlaceholders(template.getFoContent());
            
            result.put("success", true);
            result.put("data", placeholders);
            
        } catch (Exception e) {
            log.error("获取占位符失败", e);
            result.put("success", false);
            result.put("message", "获取占位符失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 导出PDF
     */
    @PostMapping("/{id}/export")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        try {
            PdfTemplate template = templateMapper.selectById(id);
            if (template == null || template.getFoContent() == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 1. 保存导出记录
            PdfRecord record = new PdfRecord();
            record.setTemplateId(id);
            record.setDataJson(JSON.toJSONString(data.get("formData")));
            recordMapper.insert(record);
            
            // 2. 填充数据并渲染PDF
            @SuppressWarnings("unchecked")
            Map<String, Object> formData = (Map<String, Object>) data.get("formData");
            byte[] pdfBytes = pdfExportService.fillAndRender(template.getFoContent(), formData);
            
            // 3. 上传PDF到MinIO
            String pdfKey = "exports/" + UUID.randomUUID() + "-" + template.getName() + ".pdf";
            minioService.uploadFile(
                    new java.io.ByteArrayInputStream(pdfBytes),
                    template.getName() + ".pdf",
                    "application/pdf"
            );
            
            // 4. 更新记录
            record.setExportedPdfKey(pdfKey);
            recordMapper.updateById(record);
            
            // 5. 返回PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", template.getName() + ".pdf");
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 下载导出记录PDF
     */
    @GetMapping("/record/{recordId}/download")
    public ResponseEntity<byte[]> downloadRecord(@PathVariable Long recordId) {
        try {
            PdfRecord record = recordMapper.selectById(recordId);
            if (record == null || record.getExportedPdfKey() == null) {
                return ResponseEntity.notFound().build();
            }
            
            InputStream stream = minioService.downloadFile(record.getExportedPdfKey());
            byte[] pdfBytes = stream.readAllBytes();
            
            PdfTemplate template = templateMapper.selectById(record.getTemplateId());
            String filename = template != null ? template.getName() : "export";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename + ".pdf");
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("下载失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
