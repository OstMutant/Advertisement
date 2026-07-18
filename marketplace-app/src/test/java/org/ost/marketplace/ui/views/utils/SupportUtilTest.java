package org.ost.marketplace.ui.views.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SupportUtil#toLongOrNull} replaced the old {@code Double}-backed {@code toLong()}, which
 * silently truncated fractional input (e.g. "123.99" -> 123) with no error shown
 * (improvement-061). Parsing directly from the raw text avoids the truncation entirely: a
 * fractional or otherwise non-whole-number string returns null instead of a wrong value, so the
 * caller (here, {@code QueryLongField}) can flag it as invalid rather than silently filtering on
 * the wrong id.
 */
class SupportUtilTest {

    @Test
    void toLongOrNull_wholeNumber_parses() {
        assertThat(SupportUtil.toLongOrNull("123")).isEqualTo(123L);
    }

    @Test
    void toLongOrNull_negativeWholeNumber_parses() {
        assertThat(SupportUtil.toLongOrNull("-5")).isEqualTo(-5L);
    }

    @Test
    void toLongOrNull_surroundingWhitespace_trimsAndParses() {
        assertThat(SupportUtil.toLongOrNull("  42  ")).isEqualTo(42L);
    }

    @Test
    void toLongOrNull_fractionalInput_returnsNull() {
        assertThat(SupportUtil.toLongOrNull("123.99")).isNull();
    }

    @Test
    void toLongOrNull_nonNumericInput_returnsNull() {
        assertThat(SupportUtil.toLongOrNull("abc")).isNull();
    }

    @Test
    void toLongOrNull_blank_returnsNull() {
        assertThat(SupportUtil.toLongOrNull("   ")).isNull();
    }

    @Test
    void toLongOrNull_null_returnsNull() {
        assertThat(SupportUtil.toLongOrNull(null)).isNull();
    }

    @Test
    void toLongOrNull_beyondLongRange_returnsNull() {
        assertThat(SupportUtil.toLongOrNull("99999999999999999999")).isNull();
    }
}
