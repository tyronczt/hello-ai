package cn.tyron.llm.pdfexport.config;

import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;

@Component
public class FopConfig {

    @Value("${spring.thymeleaf.prefix}")
    private String templatePrefix;

    private FopFactory fopFactory;

    @PostConstruct
    public void init() throws Exception {
        // 从classpath复制fop.xconf到临时目录，因为FOP需要文件系统路径
        Resource resource = new ClassPathResource("templates/fop.xconf");
        
        // 创建临时配置文件
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "fop-config");
        tempDir.mkdirs();
        File confFile = new File(tempDir, "fop.xconf");
        
        // 复制字体文件到临时目录
        File fontsDir = new File(tempDir, "fonts");
        fontsDir.mkdirs();
        copyFont("fonts/msyh.ttc", fontsDir);
        copyFont("fonts/msyhbd.ttc", fontsDir);
        copyFont("fonts/simsun.ttc", fontsDir);
        copyFont("fonts/simhei.ttf", fontsDir);
        copyFont("fonts/STSong.ttf", fontsDir);

        // 复制并替换字体路径
        String confContent = new String(resource.getInputStream().readAllBytes());
        confContent = confContent.replace("fonts/", tempDir.getAbsolutePath() + "/fonts/");
        try (FileOutputStream fos = new FileOutputStream(confFile)) {
            fos.write(confContent.getBytes());
        }

        // 构建FopFactory
        FopFactoryBuilder builder = new FopFactoryBuilder(URI.create(confFile.toURI().toURL().toString()));
        this.fopFactory = builder.build();
    }

    private void copyFont(String fontPath, File fontsDir) throws Exception {
        Resource resource = new ClassPathResource(fontPath);
        if (resource.exists()) {
            String fileName = resource.getFilename();
            File destFile = new File(fontsDir, fileName);
            if (!destFile.exists()) {
                try (InputStream is = resource.getInputStream();
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    is.transferTo(fos);
                }
            }
        }
    }

    public FopFactory getFopFactory() {
        return fopFactory;
    }
}
