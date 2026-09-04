package org.ost.restapi.api.paging;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortQueryParserTest {

    private static final Set<String> ALLOWED = Set.of("title", "createdAt");

    @Test
    void nullParam_returnsUnsorted() {
        assertThat(SortQueryParser.parse(null, ALLOWED)).isEqualTo(Sort.unsorted());
    }

    @Test
    void blankParam_returnsUnsorted() {
        assertThat(SortQueryParser.parse("  ", ALLOWED)).isEqualTo(Sort.unsorted());
    }

    @Test
    void fieldOnly_defaultsToAscending() {
        assertThat(SortQueryParser.parse("title", ALLOWED)).isEqualTo(Sort.by(Sort.Direction.ASC, "title"));
    }

    @Test
    void fieldWithAsc_ascending() {
        assertThat(SortQueryParser.parse("title,asc", ALLOWED)).isEqualTo(Sort.by(Sort.Direction.ASC, "title"));
    }

    @Test
    void fieldWithDesc_descending() {
        assertThat(SortQueryParser.parse("createdAt,desc", ALLOWED)).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void fieldWithDesc_caseInsensitive() {
        assertThat(SortQueryParser.parse("createdAt,DESC", ALLOWED)).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void unknownField_throwsIllegalArgument() {
        assertThatThrownBy(() -> SortQueryParser.parse("secretField,asc", ALLOWED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretField");
    }
}
