package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.s3.public-url}")
    private String publicUrl;

    public String upload(String folder, String originalFilename, InputStream inputStream,
                         long contentLength, String contentType) {
        String key = folder + "/" + UUID.randomUUID() + extractExtension(originalFilename);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(inputStream, contentLength)
        );

        return buildUrl(key);
    }

    public String move(String fromUrl, String toFolder, String originalFilename) {
        String fromKey = extractKey(fromUrl);
        String toKey   = toFolder + "/" + UUID.randomUUID() + extractExtension(originalFilename);

        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(fromKey)
                .destinationBucket(bucket)
                .destinationKey(toKey)
                .build());

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fromKey)
                .build());

        return buildUrl(toKey);
    }

    public void delete(String url) {
        String key = extractKey(url);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private String buildUrl(String key) {
        String base = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        return base + key;
    }

    private String extractKey(String url) {
        String prefix = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        return url;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}