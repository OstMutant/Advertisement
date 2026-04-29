package org.ost.advertisement.spi.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface StorageService {
    String upload(String folder, String originalFilename, InputStream inputStream, long contentLength, String contentType);

    String move(String fromUrl, String toFolder, String originalFilename);

    void delete(String url);

    List<String> listByPrefix(String prefix, Instant uploadedBefore);
}
