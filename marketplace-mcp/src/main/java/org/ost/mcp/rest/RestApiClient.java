package org.ost.mcp.rest;

import lombok.NonNull;
import org.ost.mcp.config.McpProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

/** Calls the marketplace REST API with the caller's own {@code Authorization} header and never throws on a non-2xx answer. */
@Component
public class RestApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final CallerAuthorization callerAuthorization;

    public RestApiClient(RestClient.Builder builder, McpProperties properties, CallerAuthorization callerAuthorization) {
        this.baseUrl = properties.restBaseUrl();
        this.restClient = builder.build();
        this.callerAuthorization = callerAuthorization;
    }

    /** Sends a GET for an already encoded path with query and returns whatever the API answered. */
    public RestAnswer get(@NonNull String pathAndQuery) {
        return restClient.get()
                .uri(URI.create(baseUrl + pathAndQuery))
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> callerAuthorization.header().ifPresent(value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                .exchange((_, response) -> {
                    String body = response.bodyTo(String.class);
                    return new RestAnswer(response.getStatusCode().value(), response.getHeaders(), body == null ? "" : body);
                });
    }
}
