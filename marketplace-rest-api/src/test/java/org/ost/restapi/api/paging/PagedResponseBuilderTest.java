package org.ost.restapi.api.paging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link PagedResponseBuilder}'s header assembly. */
class PagedResponseBuilderTest {

    private static MockHttpServletRequest request(String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/advertisements");
        request.setQueryString(queryString);
        return request;
    }

    @Test
    void singlePage_bodyAndTotalCountOnly() {
        ResponseEntity<List<String>> result = PagedResponseBuilder.build(request(null), 0, 20, 5, List.of("a", "b"));

        assertThat(result.getBody()).containsExactly("a", "b");
        assertThat(result.getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
        assertThat(result.getHeaders().get(HttpHeaders.LINK)).isNull();
    }

    @Test
    void multiPage_linkHeaderCarriesTheRequestPath() {
        ResponseEntity<List<String>> result = PagedResponseBuilder.build(request(null), 0, 20, 45, List.of());

        assertThat(result.getHeaders().getFirst(HttpHeaders.LINK))
                .contains("<http://localhost/api/advertisements?page=1&size=20>; rel=\"next\"")
                .contains("<http://localhost/api/advertisements?page=2&size=20>; rel=\"last\"");
    }

    @Test
    void multiPage_linkHeaderKeepsTheFiltersOfTheRequest() {
        ResponseEntity<List<String>> result = PagedResponseBuilder.build(request("adKind=OFFER&page=0"), 0, 20, 45, List.of());

        assertThat(result.getHeaders().getFirst(HttpHeaders.LINK))
                .contains("adKind=OFFER")
                .contains("/api/advertisements?adKind=OFFER&page=1&size=20");
    }
}
