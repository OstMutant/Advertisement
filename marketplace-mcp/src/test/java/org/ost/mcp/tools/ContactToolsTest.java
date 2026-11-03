package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ost.mcp.tools.AbstractToolTest.structured;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers the URLs, methods, result shapes and error mapping of {@link ContactTools}. */
class ContactToolsTest extends AbstractToolTest {

    private static final String REVEAL_BODY = "{\"channel\":\"PHONE\"}";
    private static final String REVEALED = "{\"channel\":\"PHONE\",\"value\":\"+380501234567\"}";

    private ContactTools tools;

    @BeforeEach
    void setUp() {
        tools = new ContactTools(restApi);
    }

    @Test
    void getAdvertisementContactReturnsChannels() {
        server.expect(requestTo("http://api.test/api/advertisements/7/contact"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withSuccess("{\"channels\":[\"PHONE\",\"TELEGRAM\"]}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getAdvertisementContact(7L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("channels", List.of("PHONE", "TELEGRAM"));
        server.verify();
    }

    @Test
    void getAdvertisementContactNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/9/contact")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getAdvertisementContact(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getProviderProfileContactReturnsChannels() {
        server.expect(requestTo("http://api.test/api/provider-profiles/5/contact"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNull())
                .andRespond(withSuccess("{\"channels\":[\"VIBER\"]}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getProviderProfileContact(5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("channels", List.of("VIBER"));
        server.verify();
    }

    @Test
    void getProviderProfileContactNotFoundBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/9/contact")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getProviderProfileContact(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void revealAdvertisementContactPostsChannelAndReturnsItem() {
        server.expect(requestTo("http://api.test/api/advertisements/7/contact/reveals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string(REVEAL_BODY))
                .andRespond(withSuccess(REVEALED, MediaType.APPLICATION_JSON));

        CallToolResult result = tools.revealAdvertisementContact(7L, "PHONE");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("item", Map.of("channel", "PHONE", "value", "+380501234567"));
        server.verify();
    }

    @Test
    void revealAdvertisementContactRateLimitedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/advertisements/7/contact/reveals"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{\"message\":\"Too many reveals\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.revealAdvertisementContact(7L, "PHONE");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result))
                .startsWith("HTTP 429: rate limit reached. Retry after 30 seconds");
    }

    @Test
    void revealProviderProfileContactPostsChannelAndReturnsItem() {
        server.expect(requestTo("http://api.test/api/provider-profiles/5/contact/reveals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(content().string(REVEAL_BODY))
                .andRespond(withSuccess(REVEALED, MediaType.APPLICATION_JSON));

        CallToolResult result = tools.revealProviderProfileContact(5L, "PHONE");

        assertThat(result.isError()).isFalse();
        assertThat(structured(result))
                .containsEntry("status", 200)
                .containsEntry("item", Map.of("channel", "PHONE", "value", "+380501234567"));
        server.verify();
    }

    @Test
    void revealProviderProfileContactRateLimitedBecomesErrorResult() {
        server.expect(requestTo("http://api.test/api/provider-profiles/5/contact/reveals"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{\"message\":\"Too many reveals\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CallToolResult result = tools.revealProviderProfileContact(5L, "PHONE");

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result))
                .startsWith("HTTP 429: rate limit reached. Retry after 30 seconds");
    }

    @Test
    void getProviderProfileContactViewsForwardsCallerAuthorization() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/5/contact/views"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{\"PHONE\":3}", MediaType.APPLICATION_JSON));

        CallToolResult result = tools.getProviderProfileContactViews(5L);

        assertThat(result.isError()).isFalse();
        assertThat(structured(result)).containsEntry("PHONE", 3);
        server.verify();
    }

    @Test
    void getProviderProfileContactViewsNotFoundBecomesErrorResult() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/9/contact/views")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CallToolResult result = tools.getProviderProfileContactViews(9L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 404");
    }

    @Test
    void getProviderProfileContactViewsForbiddenBecomesErrorResult() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/provider-profiles/5/contact/views")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        CallToolResult result = tools.getProviderProfileContactViews(5L);

        assertThat(result.isError()).isTrue();
        assertThat(errorText(result)).startsWith("HTTP 403");
    }
}
