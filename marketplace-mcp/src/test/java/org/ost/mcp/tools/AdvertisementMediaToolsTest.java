package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcp.tools.AdvertisementMediaTools.MediaFile;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** Covers the URLs, methods, bodies and error mapping of {@link AdvertisementMediaTools}. */
class AdvertisementMediaToolsTest extends AbstractToolTest {

    private static final String BYTES_BASE64 = Base64.getEncoder().encodeToString(new byte[]{1, 2});
    private static final String UPLOADED = "[{\"id\":1,\"url\":\"u\",\"filename\":\"a.png\",\"contentType\":\"image/png\"}]";
    private static final String VIDEO_BODY = "{\"url\":\"https://youtu.be/abc\"}";
    private static final String VIDEO_CREATED = "{\"id\":2,\"url\":\"https://youtu.be/abc\",\"filename\":null,\"contentType\":\"video/youtube\"}";

    private AdvertisementMediaTools tools;

    @BeforeEach
    void setUp() {
        tools = new AdvertisementMediaTools(restApi);
    }

    @Test
    void uploadAdvertisementMediaSendsEveryFileAsMultipart() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/5/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"files\"; filename=\"a.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andExpect(content().string(containsString("name=\"files\"; filename=\"b.mp4\"")))
                .andExpect(content().string(containsString("Content-Type: video/mp4")))
                .andRespond(withStatus(HttpStatus.CREATED).body(UPLOADED).contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.uploadAdvertisementMedia(5L, List.of(
                new MediaFile("a.png", "image/png", BYTES_BASE64),
                new MediaFile("b.mp4", "video/mp4", BYTES_BASE64)));

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 201);
        server.verify();
    }

    @Test
    void uploadAdvertisementMediaInvalidBase64SendsNothing() {
        CallToolResult result = tools.uploadAdvertisementMedia(5L, List.of(new MediaFile("a.png", "image/png", "!!!")));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("contentBase64 of file a.png");
        server.verify();
    }

    @Test
    void uploadAdvertisementMediaForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.uploadAdvertisementMedia(5L, List.of(new MediaFile("a.png", "image/png", BYTES_BASE64)));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void uploadAdvertisementMediaNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/9/media")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.uploadAdvertisementMedia(9L, List.of(new MediaFile("a.png", "image/png", BYTES_BASE64)));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void uploadAdvertisementMediaLimitBecomesErrorResultWithServerMessage() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"A gallery holds at most 10 items\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.uploadAdvertisementMedia(5L, List.of(new MediaFile("a.png", "image/png", BYTES_BASE64)));

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result))
                .isEqualTo("HTTP 400: A gallery holds at most 10 items");
    }

    @Test
    void addAdvertisementVideoPostsUrlAndReturnsCreatedItem() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/5/media/videos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string(VIDEO_BODY))
                .andRespond(withStatus(HttpStatus.CREATED).body(VIDEO_CREATED).contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.addAdvertisementVideo(5L, "https://youtu.be/abc");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 201);
        server.verify();
    }

    @Test
    void addAdvertisementVideoUnsupportedHostBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media/videos"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"Unsupported video host\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.addAdvertisementVideo(5L, "https://example.com/v");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).isEqualTo("HTTP 400: Unsupported video host");
    }

    @Test
    void addAdvertisementVideoForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media/videos")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.addAdvertisementVideo(5L, "https://youtu.be/abc");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }

    @Test
    void deleteAdvertisementMediaSendsDeleteWithoutBody() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/advertisements/5/media/9"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        CallToolResult result = tools.deleteAdvertisementMedia(5L, 9L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("status", 204);
        server.verify();
    }

    @Test
    void deleteAdvertisementMediaNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.deleteAdvertisementMedia(5L, 9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void deleteAdvertisementMediaForbiddenBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/5/media/9")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.deleteAdvertisementMedia(5L, 9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
