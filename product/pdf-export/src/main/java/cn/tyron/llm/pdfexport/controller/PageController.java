package cn.tyron.llm.pdfexport.controller;

import cn.tyron.llm.pdfexport.entity.PdfRecord;
import cn.tyron.llm.pdfexport.entity.PdfTemplate;
import cn.tyron.llm.pdfexport.mapper.PdfRecordMapper;
import cn.tyron.llm.pdfexport.mapper.PdfTemplateMapper;
import cn.tyron.llm.pdfexport.service.MinioService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class PageController {

    private final PdfTemplateMapper templateMapper;
    private final PdfRecordMapper recordMapper;
    private final MinioService minioService;

    @GetMapping("/")
    public String index(Model model,
                       @RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize) {
        Page<PdfTemplate> page = new Page<>(pageNum, pageSize);
        Page<PdfTemplate> result = templateMapper.selectPage(page, 
                new LambdaQueryWrapper<PdfTemplate>()
                        .orderByDesc(PdfTemplate::getCreateTime));
        
        model.addAttribute("page", result);
        return "index";
    }

    @GetMapping("/template/{id}")
    public String detail(@PathVariable Long id, Model model) {
        PdfTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return "redirect:/";
        }
        
        String pdfUrl = null;
        if (template.getPdfObjectKey() != null) {
            try {
                pdfUrl = minioService.getPresignedUrl(template.getPdfObjectKey());
            } catch (Exception e) {
                // 忽略
            }
        }
        
        model.addAttribute("template", template);
        model.addAttribute("pdfUrl", pdfUrl);
        return "detail";
    }

    @GetMapping("/template/{id}/preview")
    public String previewPage(@PathVariable Long id, Model model) {
        PdfTemplate template = templateMapper.selectById(id);
        if (template == null || template.getFoContent() == null) {
            return "redirect:/template/" + id;
        }
        
        model.addAttribute("template", template);
        return "preview";
    }

    @GetMapping("/export/{id}")
    public String exportPage(@PathVariable Long id, Model model) {
        PdfTemplate template = templateMapper.selectById(id);
        if (template == null || template.getFoContent() == null) {
            return "redirect:/template/" + id;
        }
        
        model.addAttribute("template", template);
        
        // 获取该模板的导出记录
        List<PdfRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<PdfRecord>()
                        .eq(PdfRecord::getTemplateId, id)
                        .orderByDesc(PdfRecord::getCreateTime)
                        .last("LIMIT 10"));
        model.addAttribute("records", records);
        
        return "export";
    }

    @GetMapping("/records")
    public String records(Model model,
                         @RequestParam(defaultValue = "1") int pageNum,
                         @RequestParam(defaultValue = "10") int pageSize) {
        Page<PdfRecord> page = new Page<>(pageNum, pageSize);
        Page<PdfRecord> result = recordMapper.selectPage(page, 
                new LambdaQueryWrapper<PdfRecord>()
                        .orderByDesc(PdfRecord::getCreateTime));
        
        // 填充模板名称
        for (PdfRecord record : result.getRecords()) {
            PdfTemplate template = templateMapper.selectById(record.getTemplateId());
            if (template != null) {
                record.setTemplateName(template.getName());
            }
        }
        
        model.addAttribute("page", result);
        return "records";
    }
}
