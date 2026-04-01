package cn.tyron.llm.pdfexport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pdf_record")
public class PdfRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String dataJson;

    private String exportedPdfKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String templateName;
}
