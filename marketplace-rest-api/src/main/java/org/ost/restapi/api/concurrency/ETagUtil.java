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

    /** Like {@link #parseIfMatch} for a call that cannot run without the caller's version: a missing or blank header is a bad request. */
    public static Long requireIfMatch(String ifMatch) {
        Long version = parseIfMatch(ifMatch);
        if (version == null) {
            throw new IllegalArgumentException("The If-Match header with the resource's current version is required");
        }
        return version;
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
