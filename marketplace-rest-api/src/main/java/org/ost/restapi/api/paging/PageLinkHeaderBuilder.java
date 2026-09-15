package org.ost.restapi.api.paging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/** Builds an RFC 8288 {@code Link} header ({@code first}/{@code prev}/{@code next}/{@code last}) for a page of results. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageLinkHeaderBuilder {

    public static String build(UriComponentsBuilder uriBuilder, int page, int size, int totalElements) {
        if (size <= 0) {
            return "";
        }
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<String> links = new ArrayList<>();
        if (page > 0) {
            links.add(linkFor(uriBuilder, 0, size, "first"));
            links.add(linkFor(uriBuilder, page - 1, size, "prev"));
        }
        if (page + 1 < totalPages) {
            links.add(linkFor(uriBuilder, page + 1, size, "next"));
            links.add(linkFor(uriBuilder, totalPages - 1, size, "last"));
        }
        return String.join(", ", links);
    }

    private static String linkFor(UriComponentsBuilder uriBuilder, int page, int size, String rel) {
        String url = uriBuilder.cloneBuilder()
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .build()
                .toUriString();
        return "<%s>; rel=\"%s\"".formatted(url, rel);
    }
}
