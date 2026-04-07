package org.ost.storage.api;

import java.io.InputStream;

public interface StorageService {
    String upload(String folder, String originalFilename, InputStream inputStream, long contentLength, String contentType);

    String move(String fromUrl, String toFolder, String originalFilename);

    void delete(String url);
}