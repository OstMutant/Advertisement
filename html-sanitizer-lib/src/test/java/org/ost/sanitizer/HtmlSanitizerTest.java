package org.ost.sanitizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlSanitizerTest {

    @Test
    void sanitize_disallowedTag_isStripped() {
        assertThat(HtmlSanitizer.sanitize("<script>alert(1)</script><p>hi</p>", 100))
                .isEqualTo("<p>hi</p>");
    }

    @Test
    void sanitize_formattingLinksBlocksAndPre_arePreserved() {
        String html = "<p><strong>bold</strong> <a href=\"https://example.com\">link</a></p><pre>code</pre>";
        assertThat(HtmlSanitizer.sanitize(html, 100)).isEqualTo(html);
    }

    @Test
    void sanitize_visibleTextAtMaxLength_isAllowed() {
        String html = "<p>" + "a".repeat(10) + "</p>";
        assertThat(HtmlSanitizer.sanitize(html, 10)).isEqualTo(html);
    }

    @Test
    void sanitize_visibleTextOverMaxLength_throws() {
        String html = "<p>" + "a".repeat(11) + "</p>";
        assertThatThrownBy(() -> HtmlSanitizer.sanitize(html, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void sanitize_tagsDoNotCountTowardVisibleLength() {
        String html = "<p><strong><em>" + "a".repeat(10) + "</em></strong></p>";
        assertThat(HtmlSanitizer.sanitize(html, 10)).isEqualTo(html);
    }

    @Test
    void sanitize_blankInput_isReturnedUnchanged() {
        assertThat(HtmlSanitizer.sanitize("   ", 10)).isEqualTo("   ");
    }
}
