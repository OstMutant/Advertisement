package org.ost.mcp.rest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers encoding and null skipping of {@link RestPath}. */
class RestPathTest {

    @Test
    void skipsNullAndEmptyValuesAndJoinsCollections() {
        String path = RestPath.of("/api/a/{id}", 5L)
                .param("title", (Object) null)
                .param("ids", List.of(1L, 2L))
                .param("none", List.of())
                .param("page", 0)
                .build();

        assertThat(path).isEqualTo("/api/a/5?ids=1%2C2&page=0");
    }

    @Test
    void encodesReservedCharactersInValues() {
        String path = RestPath.of("/api/a").param("title", "a&b=c+d é").build();

        assertThat(path).isEqualTo("/api/a?title=a%26b%3Dc%2Bd%20%C3%A9");
    }
}
