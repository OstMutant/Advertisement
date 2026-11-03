package org.ost.mcp.rest;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;

/** Serializes a request body to JSON text and names the content types the REST API accepts for it. */
@UtilityClass
public class JsonBody {

    public static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    public static final String MERGE_PATCH = "application/merge-patch+json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public String of(@NonNull Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public String of(@NonNull String key, Object value) {
        return MAPPER.writeValueAsString(Collections.singletonMap(key, value));
    }
}
