package org.ost.attachment.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentContentTypeValidatorTest {

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] GIF_BYTES = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] WEBP_BYTES = {
            'R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ',
            0x0E, 0x00, 0x00, 0x00
    };

    // "isom" brand (common real-world case) needs the .mp4 filename hint -- magic alone matches quicktime too.
    private static final byte[] MP4_BYTES = {
            0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0x00, 0x00, 0x02, 0x00,
            'i', 's', 'o', 'm', 'i', 's', 'o', '2', 'a', 'v', 'c', '1', 'm', 'p', '4', '1'
    };

    private static final byte[] WEBM_BYTES = {
            0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F,
            0x42, (byte) 0x86, (byte) 0x81, 0x01,
            0x42, (byte) 0xF7, (byte) 0x81, 0x01,
            0x42, (byte) 0xF2, (byte) 0x81, 0x04,
            0x42, (byte) 0xF3, (byte) 0x81, 0x08,
            0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm',
            0x42, (byte) 0x87, (byte) 0x81, 0x02,
            0x42, (byte) 0x85, (byte) 0x81, 0x02
    };

    private static final byte[] HTML_BYTES =
            "<!DOCTYPE html><html><head></head><body><script>alert(1)</script></body></html>"
                    .getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, JPEG, photo.jpg",
            "image/png, PNG, photo.png",
            "image/gif, GIF, photo.gif",
            "image/webp, WEBP, photo.webp",
            "video/mp4, MP4, clip.mp4",
            "video/webm, WEBM, clip.webm",
    })
    void validate_allowedTypeWithMatchingBytes_returnsReadableStream(String contentType, String fixture, String filename)
            throws Exception {
        byte[] bytes = bytesFor(fixture);
        InputStream validated = AttachmentContentTypeValidator.validate(new ByteArrayInputStream(bytes), filename, contentType);
        assertThat(validated.readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void validate_disallowedDeclaredType_throwsAndClosesStream() {
        TrackingInputStream stream = new TrackingInputStream(HTML_BYTES);
        assertThatThrownBy(() -> AttachmentContentTypeValidator.validate(stream, "page.html", "text/html"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported attachment content type");
        assertThat(stream.closed).isTrue();
    }

    @Test
    void validate_allowedDeclaredTypeButMismatchedContent_throwsAndClosesStream() {
        TrackingInputStream stream = new TrackingInputStream(HTML_BYTES);
        assertThatThrownBy(() -> AttachmentContentTypeValidator.validate(stream, "photo.jpg", "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match its declared type");
        assertThat(stream.closed).isTrue();
    }

    @Test
    void validate_streamReadFailure_throwsUncheckedIOException() {
        assertThatThrownBy(() -> AttachmentContentTypeValidator.validate(new ThrowingInputStream(), "photo.jpg", "image/jpeg"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to inspect attachment content");
    }

    @Test
    void validate_disallowedDeclaredType_closeFailure_stillThrowsIllegalArgumentException() {
        CloseFailingInputStream stream = new CloseFailingInputStream(HTML_BYTES);
        assertThatThrownBy(() -> AttachmentContentTypeValidator.validate(stream, "page.html", "text/html"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported attachment content type");
    }

    private static final class ThrowingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("boom");
        }
    }

    private static final class CloseFailingInputStream extends FilterInputStream {
        private CloseFailingInputStream(byte[] bytes) {
            super(new ByteArrayInputStream(bytes));
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    private static final class TrackingInputStream extends FilterInputStream {
        private boolean closed = false;

        private TrackingInputStream(byte[] bytes) {
            super(new ByteArrayInputStream(bytes));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static byte[] bytesFor(String fixture) {
        return switch (fixture) {
            case "JPEG" -> JPEG_BYTES;
            case "PNG" -> PNG_BYTES;
            case "GIF" -> GIF_BYTES;
            case "WEBP" -> WEBP_BYTES;
            case "MP4" -> MP4_BYTES;
            case "WEBM" -> WEBM_BYTES;
            default -> throw new IllegalArgumentException(fixture);
        };
    }
}
