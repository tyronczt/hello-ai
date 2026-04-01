-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS pdf_export DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pdf_export;

-- PDF模板表
CREATE TABLE IF NOT EXISTS pdf_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '模板名称',
    description TEXT COMMENT '模板描述',
    pdf_object_key VARCHAR(512) COMMENT 'MinIO中PDF文件的存储路径',
    fo_content LONGTEXT COMMENT '转换后的XSL-FO模板内容',
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED-已上传, CONVERTING-转换中, CONVERTED-已转换, ERROR-转换失败',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PDF模板表';

-- PDF导出记录表
CREATE TABLE IF NOT EXISTS pdf_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '关联的模板ID',
    data_json LONGTEXT NOT NULL COMMENT '填充数据的JSON格式',
    exported_pdf_key VARCHAR(512) COMMENT 'MinIO中导出PDF的存储路径',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_template_id (template_id),
    INDEX idx_create_time (create_time),
    CONSTRAINT fk_record_template FOREIGN KEY (template_id) REFERENCES pdf_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PDF导出记录表';
