package org.ost.mcp.rest;

import lombok.NonNull;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Builds a REST path from a template and query parameters; null query values and empty collections are skipped, and a collection is sent as one comma-joined value. */
public final class RestPath {

    private final UriComponentsBuilder builder;
    private final List<Object> values = new ArrayList<>();

    private RestPath(UriComponentsBuilder builder, Object[] pathVariables) {
        this.builder = builder;
        values.addAll(List.of(pathVariables));
    }

    public static RestPath of(@NonNull String pathTemplate, @NonNull Object... pathVariables) {
        return new RestPath(UriComponentsBuilder.fromPath(pathTemplate), pathVariables);
    }

    public RestPath param(@NonNull String name, Object value) {
        if (value != null) {
            builder.queryParam(name, "{q" + values.size() + "}");
            values.add(value);
        }
        return this;
    }

    public RestPath param(@NonNull String name, Collection<?> value) {
        if (value == null || value.isEmpty()) {
            return this;
        }
        return param(name, (Object) value.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    public String build() {
        return builder.encode().buildAndExpand(values.toArray()).toUriString();
    }
}
