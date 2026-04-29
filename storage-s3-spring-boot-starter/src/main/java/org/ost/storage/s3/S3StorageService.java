package org.ost.storage.s3;

import org.ost.advertisement.spi.storage.StorageService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrl;

    public S3StorageService(S3Client s3Client, String bucket, String publicUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicUrl = publicUrl;
    }

    @Override
    public String upload(String folder, String originalFilename, InputStream inputStream, long contentLength, String contentType) {
        String key = folder + "/" + UUID.randomUUID() + extractExtension(originalFilename);
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(contentLength).build(),
                RequestBody.fromInputStream(inputStream, contentLength)
        );
        return buildUrl(key);
    }

    @Override
    public String move(String fromUrl, String toFolder, String originalFilename) {
        String fromKey = extractKey(fromUrl);
        String toKey = toFolder + "/" + UUID.randomUUID() + extractExtension(originalFilename);
        s3Client.copyObject(CopyObjectRequest.builder().sourceBucket(bucket).sourceKey(fromKey).destinationBucket(bucket).destinationKey(toKey).build());
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fromKey).build());
        return buildUrl(toKey);
    }

    @Override
    public void delete(String url) {
        String key = extractKey(url);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public List<String> listByPrefix(String prefix, Instant uploadedBefore) {
        ListObjectsV2Iterable pages = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build());
        List<String> result = new ArrayList<>();
        pages.contents().stream()
                .filter(obj -> obj.lastModified().isBefore(uploadedBefore))
                .forEach(obj -> result.add(buildUrl(obj.key())));
        return result;
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