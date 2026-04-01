package cn.tyron.llm.pdfexport.service;

import cn.tyron.llm.pdfexport.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public String uploadFile(InputStream inputStream, String originalFilename, String contentType) throws Exception {
        String objectKey = "pdfs/" + UUID.randomUUID() + "-" + originalFilename;
        
        // 确保bucket存在
        ensureBucketExists();
        
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .stream(inputStream, -1, 10485760)
                .contentType(contentType)
                .build());
        
        log.info("文件上传成功: {}", objectKey);
        return objectKey;
    }

    public InputStream downloadFile(String objectKey) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .build());
    }

    public void deleteFile(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .build());
        log.info("文件删除成功: {}", objectKey);
    }

    public String getPresignedUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .expiry(3600)
                .build());
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioConfig.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .build());
            log.info("Bucket创建成功: {}", minioConfig.getBucket());
        }
    }
}
