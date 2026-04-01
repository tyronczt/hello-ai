package cn.tyron.llm.pdfexport;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.tyron.llm.pdfexport.mapper")
public class PdfExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfExportApplication.class, args);
    }
}
