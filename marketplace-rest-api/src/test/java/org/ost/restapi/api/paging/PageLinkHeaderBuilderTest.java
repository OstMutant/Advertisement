package org.ost.restapi.api.paging;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class PageLinkHeaderBuilderTest {

    private static UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString("http://localhost/api/advertisements?size=20");
    }

    @Test
    void emptyResult_noLinks() {
        assertThat(PageLinkHeaderBuilder.build(baseUri(), 0, 20, 0)).isEmpty();
    }

    @Test
    void firstPage_onlyNextAndLast() {
        String header = PageLinkHeaderBuilder.build(baseUri(), 0, 20, 45);

        assertThat(header)
                .contains("rel=\"next\"").contains("page=1")
                .contains("rel=\"last\"").contains("page=2")
                .doesNotContain("rel=\"prev\"")
                .doesNotContain("rel=\"first\"");
    }

    @Test
    void middlePage_allFourLinks() {
        String header = PageLinkHeaderBuilder.build(baseUri(), 1, 20, 45);

        assertThat(header)
                .contains("rel=\"first\"").contains("page=0")
                .contains("rel=\"prev\"")
                .contains("rel=\"next\"").contains("page=2")
                .contains("rel=\"last\"");
    }

    @Test
    void lastPage_onlyFirstAndPrev() {
        String header = PageLinkHeaderBuilder.build(baseUri(), 2, 20, 45);

        assertThat(header)
                .contains("rel=\"first\"")
                .contains("rel=\"prev\"").contains("page=1")
                .doesNotContain("rel=\"next\"")
                .doesNotContain("rel=\"last\"");
    }

    @Test
    void singlePageResult_noLinks() {
        assertThat(PageLinkHeaderBuilder.build(baseUri(), 0, 20, 5)).isEmpty();
    }
}
