package org.ost.restapi.api.error;

import org.junit.jupiter.api.Test;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.platform.core.EditWindowClosedException;
import org.ost.platform.core.OptionalComponentUnavailableException;
import org.ost.platform.core.SelfActionNotAllowedException;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.feedback.model.FeedbackNotFlaggableException;
import org.ost.platform.feedback.model.FeedbackNotModeratableException;
import org.ost.platform.taxon.model.TaxonNotDeletedException;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.restapi.api.patch.PatchValidationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ApiExceptionHandler}'s exception-to-status mappings. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleOptionalComponentUnavailable_answersWithTheMessageNamingTheMissingComponent() {
        ErrorResponse response = handler.handleOptionalComponentUnavailable(
                new OptionalComponentUnavailableException(FeedbackPort.class, new NoSuchBeanDefinitionException(FeedbackPort.class)));

        assertThat(response.message()).contains("FeedbackPort");
    }

    @Test
    void handleOptionalComponentUnavailable_isMappedToServiceUnavailable() throws NoSuchMethodException {
        ResponseStatus status = ApiExceptionHandler.class
                .getMethod("handleOptionalComponentUnavailable", OptionalComponentUnavailableException.class)
                .getAnnotation(ResponseStatus.class);

        assertThat(status.value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

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
    void handlePatchValidation_returnsFieldErrors() {
        ValidationErrorResponse response = handler.handlePatchValidation(new PatchValidationException(Map.of("title", "must not be blank")));

        assertThat(response.fieldErrors()).containsEntry("title", "must not be blank");
    }

    @Test
    void handleNotFound_returnsGenericMessage() {
        ErrorResponse response = handler.handleNotFound(new NoSuchElementException());

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void handleEditWindowClosed_returnsMessage() {
        ErrorResponse response = handler.handleEditWindowClosed(new EditWindowClosedException("Edit window has closed"));

        assertThat(response.message()).isEqualTo("Edit window has closed");
    }

    @Test
    void handleSelfAction_returnsMessage() {
        ErrorResponse response = handler.handleSelfAction(new SelfActionNotAllowedException("Cannot flag your own feedback entry"));

        assertThat(response.message()).isEqualTo("Cannot flag your own feedback entry");
    }

    @Test
    void handleFeedbackNotFlaggable_returnsMessage() {
        ErrorResponse response = handler.handleFeedbackNotFlaggable(new FeedbackNotFlaggableException("Feedback 7 is already hidden or reviewed"));

        assertThat(response.message()).isEqualTo("Feedback 7 is already hidden or reviewed");
    }

    @Test
    void handleFeedbackNotModeratable_returnsMessage() {
        ErrorResponse response = handler.handleFeedbackNotModeratable(new FeedbackNotModeratableException("Feedback 7 is not awaiting a moderation decision"));

        assertThat(response.message()).isEqualTo("Feedback 7 is not awaiting a moderation decision");
    }

    @Test
    void handleTaxonNotDeleted_returnsMessage() {
        ErrorResponse response = handler.handleTaxonNotDeleted(new TaxonNotDeletedException("Taxon 7 is not deleted"));

        assertThat(response.message()).isEqualTo("Taxon 7 is not deleted");
    }

    @Test
    void handleIllegalArgument_returnsMessage() {
        ErrorResponse response = handler.handleIllegalArgument(new IllegalArgumentException("Unknown sort field: x"));

        assertThat(response.message()).isEqualTo("Unknown sort field: x");
    }

    @Test
    void handleTooManyAttempts_returnsMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleTooManyAttempts(new TooManyAttemptsException("Too many failed attempts"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().message()).isEqualTo("Too many failed attempts");
    }

    @Test
    void handleTooManyAttempts_withWindow_setsRetryAfter() {
        ResponseEntity<ErrorResponse> response = handler.handleTooManyAttempts(
                new TooManyAttemptsException("Too many failed attempts", Duration.ofMinutes(15)));

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
    }

    @Test
    void handleTooManyAttempts_withoutWindow_hasNoRetryAfter() {
        ResponseEntity<ErrorResponse> response = handler.handleTooManyAttempts(new TooManyAttemptsException("Too many failed attempts"));

        assertThat(response.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }

    @Test
    void handleIllegalState_returnsMessage_distinctFromTooManyAttempts() {
        ErrorResponse response = handler.handleIllegalState(new IllegalStateException("No user_preferences row for actorId=1"));

        assertThat(response.message()).isEqualTo("No user_preferences row for actorId=1");
    }
}
