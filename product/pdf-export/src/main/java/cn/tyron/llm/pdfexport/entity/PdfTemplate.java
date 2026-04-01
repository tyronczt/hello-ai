package cn.tyron.llm.pdfexport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pdf_template")
public class PdfTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String pdfObjectKey;

    private String foContent;

    private String status;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
