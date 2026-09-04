package org.ost.restapi.api.error;

import org.junit.jupiter.api.Test;
import org.ost.orchestrator.services.AccessDeniedException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleAccessDenied_returnsMessage() {
        ErrorResponse response = handler.handleAccessDenied(new AccessDeniedException("not allowed"));

        assertThat(response.message()).isEqualTo("not allowed");
    }

    @Test
    void handleOptimisticLocking_returnsGenericMessage() {
        ErrorResponse response = handler.handleOptimisticLocking(new OptimisticLockingFailureException("stale"));

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
}
