package org.ost.mcp.rest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the JSON text and the content type names of {@link JsonBody}. */
class JsonBodyTest {

    /** A nested record, serialized inside a list to check record support. */
    private record Translation(String locale, String name) {}

    @Test
    void serializesAMapAndKeepsNullValues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "t");
        body.put("cityTaxonId", null);

        assertThat(JsonBody.of(body)).isEqualTo("{\"title\":\"t\",\"cityTaxonId\":null}");
    }

    @Test
    void serializesASingleKeyBody() {
        assertThat(JsonBody.of("locale", "uk")).isEqualTo("{\"locale\":\"uk\"}");
    }

    @Test
    void serializesRecordsInsideAList() {
        assertThat(JsonBody.of(Map.of("translations", List.of(new Translation("en", "Plumbing")))))
                .isEqualTo("{\"translations\":[{\"locale\":\"en\",\"name\":\"Plumbing\"}]}");
    }

    @Test
    void namesTheAcceptedContentTypes() {
        assertThat(JsonBody.JSON).isEqualTo("application/json");
        assertThat(JsonBody.MERGE_PATCH).isEqualTo("application/merge-patch+json");
    }
}
