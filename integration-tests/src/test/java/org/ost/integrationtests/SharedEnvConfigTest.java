package org.ost.integrationtests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SharedEnvConfig} resolves the repo-root {@code .env} by walking up from a starting
 * directory -- exercised here via the package-visible {@code require(String, File)} overload
 * against isolated {@code @TempDir} trees, since reassigning the {@code user.dir} system property
 * does not actually change how {@link java.io.File} resolves relative paths on this JDK (confirmed
 * directly: a {@code System.setProperty("user.dir", ...)}-based version of this test kept
 * resolving the real repo-root {@code .env} instead of the temp one). Deliberately untagged (no
 * {@code @Tag("testcontainers")}) -- pure file-walking logic, no Docker, runs under a plain
 * {@code mvn test} (improvement-047).
 */
class SharedEnvConfigTest {

    @Test
    void require_envInStartDirectory_returnsValue(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "POSTGRES_IMAGE=test-image-root\n");

        String value = SharedEnvConfig.require("POSTGRES_IMAGE", tempDir.toFile());

        assertThat(value).isEqualTo("test-image-root");
    }

    @Test
    void require_envInParentDirectory_walksUpAndReturnsValue(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "POSTGRES_IMAGE=test-image-parent\n");
        Path moduleDir = Files.createDirectory(tempDir.resolve("some-module"));

        String value = SharedEnvConfig.require("POSTGRES_IMAGE", moduleDir.toFile());

        assertThat(value).isEqualTo("test-image-parent");
    }

    @Test
    void require_noEnvFileWithinSearchRange_throwsIllegalStateException(@TempDir Path tempDir) {
        assertThatThrownBy(() -> SharedEnvConfig.require("POSTGRES_IMAGE", tempDir.toFile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not find .env");
    }

    @Test
    void require_envFilePresentButKeyMissing_throwsIllegalStateExceptionMentioningKey(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "OTHER_KEY=value\n");

        assertThatThrownBy(() -> SharedEnvConfig.require("POSTGRES_IMAGE", tempDir.toFile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_IMAGE");
    }
}
