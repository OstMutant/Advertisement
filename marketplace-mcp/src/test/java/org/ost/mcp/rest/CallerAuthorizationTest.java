package org.ost.mcp.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.CallerRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers reading the caller's Authorization header from the current servlet request. */
class CallerAuthorizationTest {

    private final CallerAuthorization authorization = new CallerAuthorization();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emptyWithoutRequest() {
        assertThat(authorization.header()).isEmpty();
    }

    @Test
    void emptyWithoutHeader() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(authorization.header()).isEmpty();
    }

    @Test
    void returnsHeaderValue() {
        CallerRequest.authorizeAs("Bearer k");

        assertThat(authorization.header()).contains("Bearer k");
    }
}
