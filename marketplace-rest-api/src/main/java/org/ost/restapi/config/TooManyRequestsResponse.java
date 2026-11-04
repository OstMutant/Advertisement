package org.ost.restapi.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.ost.platform.core.TooManyAttemptsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/** Writes the 429 JSON response with {@code Retry-After} for the rate-limit filters, whose exceptions never reach {@code ApiExceptionHandler}. */
@UtilityClass
class TooManyRequestsResponse {

    void write(@NonNull HttpServletResponse response, @NonNull TooManyAttemptsException ex) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        ex.retryAfter().ifPresent(window -> response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds())));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + ex.getMessage() + "\"}");
    }
}
