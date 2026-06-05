package org.ost.attachment.config;

import org.ost.attachment.services.S3StorageService;
import org.ost.attachment.services.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class AttachmentS3Config {

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(
            @Value("${storage.s3.endpoint}") String endpoint,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService s3StorageService(S3Client s3Client,
                                           @Value("${storage.s3.bucket}") String bucket,
                                           @Value("${storage.s3.public-url}") String publicUrl) {
        return new S3StorageService(s3Client, bucket, publicUrl);
    }
}
