package org.ost.mcp.rest;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcp.config.McpProperties;
import org.ost.mcptest.CallerRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Covers header forwarding and non-throwing answers of {@link RestApiClient}. */
class RestApiClientTest {

    private MockRestServiceServer server;
    private RestApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestApiClient(builder, new McpProperties("http://api.test", List.of()), new CallerAuthorization());
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsAuthorizationWhenPresent() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/x"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        RestAnswer answer = client.get("/api/x");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("[]");
        server.verify();
    }

    @Test
    void sendsNoAuthorizationWhenAbsent() {
        server.expect(requestTo("http://api.test/api/x"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.get("/api/x").isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void returnsNotFoundWithoutThrowing() {
        server.expect(requestTo("http://api.test/api/x"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"gone\"}").contentType(MediaType.APPLICATION_JSON));

        RestAnswer answer = client.get("/api/x");

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.body()).contains("gone");
        assertThat(answer.isSuccess()).isFalse();
    }

    @Test
    void getAsksForJson() {
        server.expect(requestTo("http://api.test/api/x"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.get("/api/x").isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void getTextAcceptsAnyMediaTypeAndForwardsAuthorization() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "*/*"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        RestAnswer answer = client.getText("/health");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void getTextReturnsNotFoundWithoutThrowing() {
        server.expect(requestTo("http://api.test/sitemap.xml"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RestAnswer answer = client.getText("/sitemap.xml");

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.isSuccess()).isFalse();
    }

    @Test
    void returnsTooManyRequestsWithHeaders() {
        server.expect(requestTo("http://api.test/api/x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "7").body("{}"));

        RestAnswer answer = client.get("/api/x");

        assertThat(answer.status()).isEqualTo(429);
        assertThat(answer.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
    }

    @Test
    void sendPostForwardsBodyContentTypeAndAuthorization() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/x"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string("{\"a\":1}"))
                .andRespond(withStatus(HttpStatus.CREATED).header(HttpHeaders.LOCATION, "/api/x/5").body("{\"id\":5}"));

        RestAnswer answer = client.send(HttpMethod.POST, "/api/x", "{\"a\":1}", MediaType.APPLICATION_JSON_VALUE, null);

        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.headers().getFirst(HttpHeaders.LOCATION)).isEqualTo("/api/x/5");
        server.verify();
    }

    @Test
    void sendPatchSendsIfMatchAndMergePatchContentType() {
        server.expect(requestTo("http://api.test/api/x/5"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"3\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/merge-patch+json"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        RestAnswer answer = client.send(HttpMethod.PATCH, "/api/x/5", "{\"title\":\"t\"}", "application/merge-patch+json", "\"3\"");

        assertThat(answer.isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void sendDeleteWithoutBodySendsNoContentTypeNorIfMatch() {
        server.expect(requestTo("http://api.test/api/x/5"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(headerDoesNotExist(HttpHeaders.IF_MATCH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        RestAnswer answer = client.send(HttpMethod.DELETE, "/api/x/5", null, null, null);

        assertThat(answer.status()).isEqualTo(204);
        assertThat(answer.body()).isEmpty();
        server.verify();
    }

    @Test
    void sendReturnsPreconditionFailedWithoutThrowing() {
        server.expect(requestTo("http://api.test/api/x/5"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED).body("{\"message\":\"stale\"}").contentType(MediaType.APPLICATION_JSON));

        RestAnswer answer = client.send(HttpMethod.PUT, "/api/x/5", "{}", MediaType.APPLICATION_JSON_VALUE, "\"1\"");

        assertThat(answer.status()).isEqualTo(412);
        assertThat(answer.body()).contains("stale");
    }

    @Test
    void postMultipartSendsEveryFileUnderTheField() {
        CallerRequest.authorizeAs("Bearer abc");
        server.expect(requestTo("http://api.test/api/x/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"files\"; filename=\"a.png\"")))
                .andExpect(content().string(containsString("name=\"files\"; filename=\"b.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andRespond(withStatus(HttpStatus.CREATED).body("[]").contentType(MediaType.APPLICATION_JSON));

        RestAnswer answer = client.postMultipart("/api/x/media", "files", List.of(
                new FilePart("a.png", "image/png", new ByteArrayResource(new byte[]{1, 2})),
                new FilePart("b.png", "image/png", new ByteArrayResource(new byte[]{3}))));

        assertThat(answer.status()).isEqualTo(201);
        server.verify();
    }
}
