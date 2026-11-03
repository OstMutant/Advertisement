package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.ost.mcp.rest.RestAnswer;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns a REST answer into an MCP tool result: a success carries the same object as text and as structured content, an error carries text only. */
@UtilityClass
public class ToolResults {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int RAW_BODY_LIMIT = 300;

    public CallToolResult success(@NonNull Object payload) {
        return CallToolResult.builder()
                .addTextContent(MAPPER.writeValueAsString(payload))
                .structuredContent(payload)
                .isError(false)
                .build();
    }

    public CallToolResult paged(@NonNull RestAnswer answer, int page) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        List<?> items = items(answer);
        boolean hasMore = nextLinkPresent(answer.headers());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("totalCount", totalCount(answer.headers()));
        payload.put("page", page);
        payload.put("returned", items.size());
        payload.put("hasMore", hasMore);
        payload.put("nextPage", hasMore ? page + 1 : null);
        return success(payload);
    }

    public CallToolResult single(@NonNull String key, @NonNull RestAnswer answer) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, parse(answer.body()));
        payload.put("etag", answer.headers().getETag());
        return success(payload);
    }

    public CallToolResult list(@NonNull RestAnswer answer) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        List<?> items = items(answer);
        Long total = totalCount(answer.headers());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("totalCount", Optional.<Number>ofNullable(total).orElse(items.size()));
        return success(payload);
    }

    public CallToolResult object(@NonNull RestAnswer answer) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        return success(parse(answer.body()));
    }

    public CallToolResult written(@NonNull RestAnswer answer) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", answer.status());
        payload.put("item", answer.body().isBlank() ? null : parse(answer.body()));
        payload.put("location", answer.headers().getFirst(HttpHeaders.LOCATION));
        payload.put("etag", answer.headers().getETag());
        return success(payload);
    }

    public CallToolResult text(@NonNull RestAnswer answer) {
        if (!answer.isSuccess()) {
            return error(answer);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", answer.status());
        payload.put("body", answer.body());
        return success(payload);
    }

    public CallToolResult error(@NonNull RestAnswer answer) {
        return CallToolResult.builder()
                .addTextContent(errorText(answer))
                .isError(true)
                .build();
    }

    private String errorText(RestAnswer answer) {
        return switch (answer.status()) {
            case 401 -> "HTTP 401: the API key is missing or invalid. Connect with the header Authorization: Bearer <your API key>.";
            case 403 -> "HTTP 403: this API key is not allowed to do that.";
            case 404 -> "HTTP 404: no such resource.";
            case 429 -> "HTTP 429: rate limit reached. Retry after "
                    + (answer.headers().getFirst(HttpHeaders.RETRY_AFTER) != null ? answer.headers().getFirst(HttpHeaders.RETRY_AFTER) : "a few")
                    + " seconds; do not retry earlier. Server message: " + messageOrEmpty(answer.body());
            default -> "HTTP " + answer.status() + ": " + serverMessage(answer.body())
                    .orElseGet(() -> answer.body().length() > RAW_BODY_LIMIT ? answer.body().substring(0, RAW_BODY_LIMIT) : answer.body());
        };
    }

    private String messageOrEmpty(String body) {
        return serverMessage(body).orElse("");
    }

    private Optional<String> serverMessage(String body) {
        try {
            JsonNode message = MAPPER.readTree(body).path("message");
            return message.isString() ? Optional.of(message.stringValue()) : Optional.empty();
        } catch (JacksonException _) {
            return Optional.empty();
        }
    }

    private List<?> items(RestAnswer answer) {
        return (List<?>) parse(answer.body());
    }

    private Object parse(String body) {
        return MAPPER.readValue(body, Object.class);
    }

    private Long totalCount(HttpHeaders headers) {
        String value = headers.getFirst("X-Total-Count");
        return value == null ? null : Long.valueOf(value.trim());
    }

    private boolean nextLinkPresent(HttpHeaders headers) {
        List<String> links = headers.get(HttpHeaders.LINK);
        return links != null && links.stream().anyMatch(link -> link.contains("rel=\"next\""));
    }
}
