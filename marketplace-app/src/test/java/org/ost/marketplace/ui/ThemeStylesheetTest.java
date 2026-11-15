package org.ost.marketplace.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the theme entry point: CSS ignores an {@code @import} that follows any other rule, which silently drops every imported component stylesheet from the build. */
class ThemeStylesheetTest {

    private static final Path STYLES = Path.of("src/main/frontend/themes/my-app/styles.css");

    @Test
    void imports_comeBeforeAnyOtherRule() throws IOException {
        String css = Files.readString(STYLES).replaceAll("(?s)/\\*.*?\\*/", "");
        int lastImport = css.lastIndexOf("@import");
        assertThat(lastImport).as("styles.css must import the component stylesheets").isNotNegative();

        String beforeLastImport = css.substring(0, lastImport)
                .replaceAll("@import[^;]*;", "")
                .replaceAll("@layer[^;{]*;", "")
                .strip();

        assertThat(beforeLastImport)
                .as("only @import and @layer statements may precede the last @import")
                .isEmpty();
    }
}
