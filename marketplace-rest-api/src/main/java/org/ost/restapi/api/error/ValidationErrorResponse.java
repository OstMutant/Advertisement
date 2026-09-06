package org.ost.restapi.api.error;

import java.util.Map;

/**
 * Per-field error body for a failed {@code @Valid} request body.
 */
public record ValidationErrorResponse(Map<String, String> fieldErrors) {
}
