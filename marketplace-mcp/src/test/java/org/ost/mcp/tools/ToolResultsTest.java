package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.ost.mcp.rest.RestAnswer;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;

/** Covers the result shapes and error texts produced by {@link ToolResults}. */
class ToolResultsTest {

    private static RestAnswer answer(int status, String body, String... headerPairs) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < headerPairs.length; i += 2) {
            headers.add(headerPairs[i], headerPairs[i + 1]);
        }
        return new RestAnswer(status, headers, body);
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @Test
    void pagedFirstPageWithNextLink() {
        CallToolResult result = ToolResults.paged(
                answer(200, "[{\"id\":1},{\"id\":2}]", "X-Total-Count", "5", "Link", "</api/advertisements?page=1>; rel=\"next\""), 0);

        Map<String, Object> body = structured(result);
        assertThat(result.isError()).isFalse();
        assertThat(body.get("totalCount")).isEqualTo(5L);
        assertThat(body).containsEntry("page", 0).containsEntry("returned", 2).containsEntry("hasMore", true).containsEntry("nextPage", 1);
        assertThat((List<?>) body.get("items")).hasSize(2);
        assertThat(text(result)).contains("\"nextPage\":1").contains("\"totalCount\":5");
    }

    @Test
    void pagedLastPageWithoutLink() {
        CallToolResult result = ToolResults.paged(answer(200, "[{\"id\":9}]", "X-Total-Count", "10", "Link", "</a?page=0>; rel=\"prev\""), 1);

        assertThat(structured(result)).containsEntry("hasMore", false).containsEntry("nextPage", null).containsEntry("returned", 1);
    }

    @Test
    void pagedTotalCountNullWhenHeaderAbsent() {
        CallToolResult result = ToolResults.paged(answer(200, "[]"), 0);

        assertThat(structured(result)).containsEntry("totalCount", null).containsEntry("hasMore", false);
        assertThat(text(result)).contains("\"totalCount\":null");
    }

    @Test
    void singleWithEtag() {
        CallToolResult result = ToolResults.single("item", answer(200, "{\"id\":3}", "ETag", "\"4\""));

        assertThat(structured(result)).containsEntry("item", Map.of("id", 3)).containsEntry("etag", "\"4\"");
    }

    @Test
    void singleWithoutEtag() {
        CallToolResult result = ToolResults.single("settings", answer(200, "{\"pageSize\":10}"));

        assertThat(structured(result)).containsEntry("settings", Map.of("pageSize", 10)).containsEntry("etag", null);
    }

    @Test
    void objectReturnsTheBodyAsIsAndMapsErrors() {
        assertThat(structured(ToolResults.object(answer(200, "{\"avgRating\":4.5,\"reviewCount\":2}"))))
                .containsEntry("avgRating", 4.5).containsEntry("reviewCount", 2);
        assertThat(ToolResults.object(answer(404, "")).isError()).isTrue();
    }

    @Test
    void listUsesHeaderOrItemCount() {
        assertThat(structured(ToolResults.list(answer(200, "[1,2,3]", "X-Total-Count", "7")))).containsEntry("totalCount", 7L);
        assertThat(structured(ToolResults.list(answer(200, "[1,2,3]")))).containsEntry("totalCount", 3);
    }

    @Test
    void writtenCarriesStatusItemLocationAndEtag() {
        CallToolResult result = ToolResults.written(answer(201, "{\"id\":5}", "Location", "/api/x/5", "ETag", "\"1\""));

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 201).containsEntry("item", Map.of("id", 5))
                .containsEntry("location", "/api/x/5").containsEntry("etag", "\"1\"");
    }

    @Test
    void writtenWithEmptyBodyHasNoItem() {
        CallToolResult result = ToolResults.written(answer(204, ""));

        assertThat(structured(result)).containsEntry("status", 204).containsEntry("item", null)
                .containsEntry("location", null).containsEntry("etag", null);
    }

    @Test
    void writtenNonSuccessBecomesAnError() {
        CallToolResult result = ToolResults.written(answer(412, "{\"message\":\"stale\"}"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("HTTP 412: stale");
        assertThat(ToolResults.written(answer(403, "")).isError()).isTrue();
    }

    @Test
    void textCarriesStatusAndRawBody() {
        CallToolResult result = ToolResults.text(answer(200, "ok", "Content-Type", "text/plain"));

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 200).containsEntry("body", "ok");
    }

    @Test
    void textNonSuccessBecomesAnError() {
        CallToolResult result = ToolResults.text(answer(404, ""));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("HTTP 404: no such resource.");
    }

    @Test
    void nonSuccessAnswersBecomeErrors() {
        assertThat(ToolResults.paged(answer(401, ""), 0).isError()).isTrue();
        assertThat(ToolResults.single("item", answer(404, "")).isError()).isTrue();
        assertThat(ToolResults.list(answer(403, "")).isError()).isTrue();
    }

    @Test
    void errorTexts() {
        assertThat(text(ToolResults.error(answer(401, "")))).isEqualTo(
                "HTTP 401: the API key is missing or invalid. Connect with the header Authorization: Bearer <your API key>.");
        assertThat(text(ToolResults.error(answer(403, "")))).isEqualTo("HTTP 403: this API key is not allowed to do that.");
        assertThat(text(ToolResults.error(answer(404, "")))).isEqualTo("HTTP 404: no such resource.");
        assertThat(text(ToolResults.error(answer(429, "{\"message\":\"slow down\"}", "Retry-After", "12")))).isEqualTo(
                "HTTP 429: rate limit reached. Retry after 12 seconds; do not retry earlier. Server message: slow down");
        assertThat(text(ToolResults.error(answer(429, "{\"message\":\"slow down\"}")))).isEqualTo(
                "HTTP 429: rate limit reached. Retry after a few seconds; do not retry earlier. Server message: slow down");
        assertThat(text(ToolResults.error(answer(400, "{\"message\":\"bad sort\"}")))).isEqualTo("HTTP 400: bad sort");
        assertThat(text(ToolResults.error(answer(500, "boom")))).isEqualTo("HTTP 500: boom");
        assertThat(text(ToolResults.error(answer(500, "x".repeat(400))))).isEqualTo("HTTP 500: " + "x".repeat(300));
        assertThat(ToolResults.error(answer(500, "")).isError()).isTrue();
    }
}
