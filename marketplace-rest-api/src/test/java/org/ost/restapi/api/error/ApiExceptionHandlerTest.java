package org.ost.restapi.api.error;

import org.junit.jupiter.api.Test;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.TooManyAttemptsException;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ApiExceptionHandler}'s exception-to-status mappings. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleAccessDenied_returnsMessage() {
        ErrorResponse response = handler.handleAccessDenied(new AccessDeniedException("not allowed"));

        assertThat(response.message()).isEqualTo("not allowed");
    }

    @Test
    void handleStaleWrite_returnsGenericMessage() {
        ErrorResponse response = handler.handleStaleWrite(new StaleWriteException("stale"));

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void handleValidation_collectsFieldErrors() {
        MethodParameter parameter = mock(MethodParameter.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("dto", "title", "must not be blank")));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ValidationErrorResponse response = handler.handleValidation(ex);

        assertThat(response.fieldErrors()).containsEntry("title", "must not be blank");
    }

    @Test
    void handleNotFound_returnsGenericMessage() {
        ErrorResponse response = handler.handleNotFound(new NoSuchElementException());

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void handleIllegalArgument_returnsMessage() {
        ErrorResponse response = handler.handleIllegalArgument(new IllegalArgumentException("Unknown sort field: x"));

        assertThat(response.message()).isEqualTo("Unknown sort field: x");
    }

    @Test
    void handleTooManyAttempts_returnsMessage() {
        ErrorResponse response = handler.handleTooManyAttempts(new TooManyAttemptsException("Too many failed attempts"));

        assertThat(response.message()).isEqualTo("Too many failed attempts");
    }

    // Confirms the fix for the real bug this rescoping closed: a genuinely unrelated
    // IllegalStateException (e.g. UserPreferencesRepository's "No user_preferences row") must
    // never be silently mapped to 429 just because it shares a supertype with rate-limit rejections.
    @Test
    void handleIllegalState_returnsMessage_distinctFromTooManyAttempts() {
        ErrorResponse response = handler.handleIllegalState(new IllegalStateException("No user_preferences row for actorId=1"));

        assertThat(response.message()).isEqualTo("No user_preferences row for actorId=1");
    }
}
