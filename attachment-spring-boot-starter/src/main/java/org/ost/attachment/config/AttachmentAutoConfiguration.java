package org.ost.attachment.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.attachment.service.DefaultAttachmentPort;
import org.ost.attachment.service.NoOpAttachmentPort;
import org.ost.attachment.service.NoOpStorageService;
import org.ost.attachment.service.S3StorageService;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.ost.platform.attachment.storage.StorageService;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.net.URI;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan("org.ost.attachment")
@EnableConfigurationProperties(CleanupProperties.class)
public class AttachmentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CleanupProperties cleanupProperties() {
        return new CleanupProperties(90);
    }

    @Bean("attachmentObjectMapper")
    @ConditionalOnMissingBean(name = "attachmentObjectMapper")
    ObjectMapper attachmentObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean("attachmentLiquibase")
    @ConditionalOnStorageEnabled
    @ConditionalOnMissingBean(name = "attachmentLiquibase")
    public SpringLiquibase attachmentLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/attachment-changelog/db.attachment-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnStorageEnabled
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
    @ConditionalOnStorageEnabled
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService s3StorageService(S3Client s3Client,
                                           @Value("${storage.s3.bucket}") String bucket,
                                           @Value("${storage.s3.public-url}") String publicUrl) {
        return new S3StorageService(s3Client, bucket, publicUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "false")
    public StorageService noOpStorageService() {
        return new NoOpStorageService();
    }

    @Bean
    @ConditionalOnStorageEnabled
    @ConditionalOnMissingBean(AttachmentPort.class)
    DefaultAttachmentPort defaultAttachmentPort(
            AttachmentService attachmentService,
            AttachmentSnapshotService attachmentSnapshotService) {
        return new DefaultAttachmentPort(attachmentService, attachmentSnapshotService);
    }

    @Bean
    @ConditionalOnMissingBean(AttachmentPort.class)
    AttachmentPort noOpAttachmentPort() {
        return new NoOpAttachmentPort();
    }
}
