package org.ost.restapi.api.paging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseBuilderTest {

    private static UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString("http://localhost/api/advertisements");
    }

    @Test
    void singlePage_bodyAndTotalCountOnly() {
        ResponseEntity<List<String>> result = PagedResponseBuilder.build(baseUri(), 0, 20, 5, List.of("a", "b"));

        assertThat(result.getBody()).containsExactly("a", "b");
        assertThat(result.getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
        assertThat(result.getHeaders().get(HttpHeaders.LINK)).isNull();
    }

    @Test
    void multiPage_addsLinkHeader() {
        ResponseEntity<List<String>> result = PagedResponseBuilder.build(baseUri(), 0, 20, 45, List.of());

        assertThat(result.getHeaders().getFirst(HttpHeaders.LINK)).contains("rel=\"next\"");
    }
}
