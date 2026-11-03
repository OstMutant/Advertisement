package org.ost.mcp.rest;

import lombok.NonNull;
import org.ost.mcp.config.McpProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Calls the marketplace REST API forwarding the caller's Authorization header; a non-2xx answer is returned as a RestAnswer, a transport failure throws RestClientException. */
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
        return send(HttpMethod.GET, pathAndQuery, null, null, null);
    }

    /** Sends a GET that accepts any media type and returns whatever the API answered. */
    public RestAnswer getText(@NonNull String pathAndQuery) {
        return exchange(newRequest(HttpMethod.GET, pathAndQuery, MediaType.ALL));
    }

    /** Sends a request with an optional String body (its contentType is required only when body is non-null) and an optional If-Match value, and returns whatever the API answered; a transport failure throws. */
    public RestAnswer send(@NonNull HttpMethod method, @NonNull String pathAndQuery, String body, String contentType, String ifMatch) {
        RestClient.RequestBodySpec request = newRequest(method, pathAndQuery);
        Optional.ofNullable(ifMatch).ifPresent(value -> request.header(HttpHeaders.IF_MATCH, value));
        Optional.ofNullable(body).ifPresent(value -> request.contentType(MediaType.parseMediaType(contentType)).body(value));
        return exchange(request);
    }

    /** Posts every part as a file of the multipart form field of that name and returns whatever the API answered. */
    public RestAnswer postMultipart(@NonNull String pathAndQuery, @NonNull String field, @NonNull List<FilePart> parts) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        parts.forEach(part -> form.part(field, part.content(), MediaType.parseMediaType(part.contentType())).filename(part.fileName()));
        RestClient.RequestBodySpec request = newRequest(HttpMethod.POST, pathAndQuery);
        request.contentType(MediaType.MULTIPART_FORM_DATA).body(form.build());
        return exchange(request);
    }

    private RestClient.RequestBodySpec newRequest(HttpMethod method, String pathAndQuery) {
        return newRequest(method, pathAndQuery, MediaType.APPLICATION_JSON);
    }

    private RestClient.RequestBodySpec newRequest(HttpMethod method, String pathAndQuery, MediaType accept) {
        return restClient.method(method)
                .uri(URI.create(baseUrl + pathAndQuery))
                .accept(accept)
                .headers(headers -> callerAuthorization.header().ifPresent(value -> headers.set(HttpHeaders.AUTHORIZATION, value)));
    }

    private static RestAnswer exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((_, response) -> {
            String body = response.bodyTo(String.class);
            return new RestAnswer(response.getStatusCode().value(), response.getHeaders(), body == null ? "" : body);
        });
    }
}
