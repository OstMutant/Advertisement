package org.ost.mcp.rest;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcp.config.McpProperties;
import org.ost.mcptest.CallerRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
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
    void returnsTooManyRequestsWithHeaders() {
        server.expect(requestTo("http://api.test/api/x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "7").body("{}"));

        RestAnswer answer = client.get("/api/x");

        assertThat(answer.status()).isEqualTo(429);
        assertThat(answer.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
    }
}
