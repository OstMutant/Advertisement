package org.ost.restapi.api.error;

/**
 * A single-message error body for {@link ApiExceptionHandler}'s non-validation mappings.
 */
public record ErrorResponse(String message) {
}
