package org.ost.restapi.api.concurrency;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

/** Converts between a resource's {@code version} and the HTTP {@code ETag}/{@code If-Match} header representation. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ETagUtil {

    /** Applies the ETag header only when a version is present -- {@code ResponseEntity.BodyBuilder.eTag()} rejects a null argument. */
    public static ResponseEntity.BodyBuilder withVersion(ResponseEntity.BodyBuilder builder, Long version) {
        return version == null ? builder : builder.eTag("\"" + version + "\"");
    }

    public static Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String stripped = ifMatch.trim();
        if (stripped.startsWith("\"") && stripped.endsWith("\"") && stripped.length() >= 2) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return Long.parseLong(stripped);
    }
}
