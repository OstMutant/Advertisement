package org.ost.restapi.api.paging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Assembles a paged list response: body plus {@code X-Total-Count} and, when applicable, a {@code Link} header. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PagedResponseBuilder {

    public static <T> ResponseEntity<List<T>> build(UriComponentsBuilder uriBuilder, int page, int size, int total, List<T> items) {
        String linkHeader = PageLinkHeaderBuilder.build(uriBuilder, page, size, total);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().header("X-Total-Count", String.valueOf(total));
        if (!linkHeader.isEmpty()) {
            response.header(HttpHeaders.LINK, linkHeader);
        }
        return response.body(items);
    }
}
