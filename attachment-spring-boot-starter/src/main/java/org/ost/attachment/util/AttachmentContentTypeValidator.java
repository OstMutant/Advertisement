package org.ost.attachment.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.ost.platform.attachment.model.AttachmentAllowedContentTypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Rejects an attachment upload whose declared or magic-byte-detected content type falls outside the allowed whitelist. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentContentTypeValidator {

    private static final Tika TIKA = new Tika();

    public static InputStream validate(InputStream inputStream, String filename, String declaredContentType) {
        if (!AttachmentAllowedContentTypes.VALUES.contains(declaredContentType)) {
            closeQuietly(inputStream);
            throw new IllegalArgumentException("Unsupported attachment content type: " + declaredContentType);
        }
        TikaInputStream tikaStream = TikaInputStream.get(inputStream);
        try {
            // Filename disambiguates: Tika's mp4/webm magic alone is ambiguous with quicktime/matroska.
            String detectedContentType = TIKA.detect(tikaStream, filename);
            if (!AttachmentAllowedContentTypes.VALUES.contains(detectedContentType)) {
                throw new IllegalArgumentException(
                        "Attachment content does not match its declared type: " + declaredContentType);
            }
        } catch (IOException e) {
            closeQuietly(tikaStream);
            throw new UncheckedIOException("Failed to inspect attachment content", e);
        } catch (IllegalArgumentException e) {
            closeQuietly(tikaStream);
            throw e;
        }
        return tikaStream;
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn("Failed to close rejected attachment upload input stream", e);
        }
    }
}
