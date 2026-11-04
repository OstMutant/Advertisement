package org.ost.restapi.api.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link ETagUtil}'s If-Match parsing. */
class ETagUtilTest {

    @Test
    void parseIfMatch_quotedVersion_isParsed() {
        assertThat(ETagUtil.parseIfMatch("\"7\"")).isEqualTo(7L);
    }

    @Test
    void parseIfMatch_blank_isNull() {
        assertThat(ETagUtil.parseIfMatch("  ")).isNull();
        assertThat(ETagUtil.parseIfMatch(null)).isNull();
    }

    @Test
    void requireIfMatch_quotedVersion_isParsed() {
        assertThat(ETagUtil.requireIfMatch("\"3\"")).isEqualTo(3L);
    }

    @Test
    void requireIfMatch_blankOrMissing_isRejected() {
        assertThatThrownBy(() -> ETagUtil.requireIfMatch(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ETagUtil.requireIfMatch(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
