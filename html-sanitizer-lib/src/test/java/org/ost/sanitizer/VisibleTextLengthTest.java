package org.ost.sanitizer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the visible-text length {@link HtmlSanitizer} measures for markup shapes the sanitizer lets through, so a change in the HTML parser behind it shows up here. */
class VisibleTextLengthTest {

    private static final int SEARCH_LIMIT = 200;

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("plain words", "<p>one two</p>", 7),
                Arguments.of("double space", "<p>one  two</p>", 7),
                Arguments.of("two paragraphs", "<p>one</p><p>two</p>", 7),
                Arguments.of("paragraphs split by a newline", "<p>one</p>\n<p>two</p>", 7),
                Arguments.of("paragraphs split by a space", "<p>a</p> <p>b</p>", 3),
                Arguments.of("list items", "<ul><li>a</li><li>b</li></ul>", 3),
                Arguments.of("pre with a line break", "<pre>line1\nline2</pre>", 11),
                Arguments.of("pre with indentation and a blank line", "<pre>  indented\n\n  two</pre>", 15),
                Arguments.of("line break element", "<p>a<br>b</p>", 3),
                Arguments.of("non-breaking space", "<p>a&nbsp;b</p>", 3),
                Arguments.of("adjacent inline elements", "<p><b>bold</b><i>it</i> tail</p>", 11),
                Arguments.of("leading and trailing spaces", "<p> leading and trailing </p>", 20),
                Arguments.of("cyrillic", "<p>Привіт світе</p>", 12),
                Arguments.of("escaped characters", "<p>a&amp;b &lt;c&gt;</p>", 7),
                Arguments.of("nested blocks", "<div><div>x</div></div>", 1),
                Arguments.of("heading then paragraph", "<h1>t</h1><p>b</p>", 3),
                Arguments.of("link text", "<p>see <a href=\"https://example.com\">this link</a> now</p>", 17)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void visibleLength_pinned(String name, String html, int expectedLength) {
        assertThat(visibleLength(html)).as(name).isEqualTo(expectedLength);
    }

    private static int visibleLength(String html) {
        for (int max = 0; max <= SEARCH_LIMIT; max++) {
            try {
                HtmlSanitizer.sanitize(html, max);
                return max;
            } catch (IllegalArgumentException tooLong) {
                // keep raising the cap until the text fits
            }
        }
        throw new IllegalStateException("visible text longer than " + SEARCH_LIMIT);
    }
}
