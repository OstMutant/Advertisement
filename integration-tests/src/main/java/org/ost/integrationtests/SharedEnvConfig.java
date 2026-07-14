package org.ost.integrationtests;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Reads the repo-root {@code .env} file — the single source of truth shared with Docker Compose
 * (which loads it natively) for values like the Postgres image tag, so the two never drift apart.
 * Locates the file by walking up from the JVM's working directory, so it resolves correctly
 * regardless of whether the build is launched from the repo root, a module subdirectory, or an
 * IDE test runner (which commonly sets the working directory to the module, not the reactor
 * root).
 */
final class SharedEnvConfig {

    private static final int MAX_PARENT_LEVELS = 5;

    private SharedEnvConfig() {
    }

    static String require(String key) {
        Properties properties = load();
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required key '" + key + "' in .env — searched up to "
                            + MAX_PARENT_LEVELS + " parent levels from "
                            + new File("").getAbsolutePath());
        }
        return value;
    }

    private static Properties load() {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i <= MAX_PARENT_LEVELS && dir != null; i++, dir = dir.getParentFile()) {
            File envFile = new File(dir, ".env");
            if (envFile.isFile()) {
                Properties properties = new Properties();
                try (FileInputStream in = new FileInputStream(envFile)) {
                    properties.load(in);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + envFile, e);
                }
                return properties;
            }
        }
        throw new IllegalStateException(
                "Could not find .env within " + MAX_PARENT_LEVELS + " parent levels of "
                        + new File("").getAbsolutePath());
    }
}
