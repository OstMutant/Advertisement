package org.ost.storage.s3;

import org.ost.storage.api.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class NoOpStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(NoOpStorageService.class);

    public NoOpStorageService() {
        log.warn("Storage is disabled (storage.enabled=false). File operations will be no-op.");
    }

    @Override
    public String upload(String folder, String originalFilename, InputStream inputStream, long contentLength, String contentType) {
        return "no-op://" + folder + "/" + originalFilename;
    }

    @Override
    public String move(String fromUrl, String toFolder, String originalFilename) {
        return fromUrl;
    }

    @Override
    public void delete(String url) {
        // no-op
    }
}