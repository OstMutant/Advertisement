package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ApiWriteRateLimitFilter} counts POST/PUT/PATCH/DELETE requests per authenticated account and blocks with 429 once the budget is used. */
class ApiWriteRateLimitFilterTest {

    private ApiWriteRateLimitFilter filter;
    private AtomicInteger chainCalls;

    @BeforeEach
    void setUp() {
        filter = new ApiWriteRateLimitFilter(new ApiWriteLimitProperties(2, Duration.ofMinutes(1)));
        chainCalls = new AtomicInteger();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thirdWrite_isBlockedWith429AndRetryAfter_withoutReachingTheHandler() throws Exception {
        authenticateAs(42L);
        run("POST");
        run("POST");

        MockHttpServletResponse blocked = run("POST");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(blocked.getContentAsString()).isEqualTo("{\"message\":\"Too many write requests, try again later\"}");
        assertThat(chainCalls).hasValue(2);
    }

    @Test
    void putPatchAndDelete_shareTheCounterWithPost() throws Exception {
        authenticateAs(42L);
        run("PUT");
        run("PATCH");

        assertThat(run("DELETE").getStatus()).isEqualTo(429);
    }

    @Test
    void blockedAccount_canStillRead() throws Exception {
        authenticateAs(42L);
        run("POST");
        run("POST");

        assertThat(run("GET").getStatus()).isEqualTo(200);
    }

    @Test
    void anotherAccount_hasItsOwnCounter() throws Exception {
        authenticateAs(42L);
        run("POST");
        run("POST");
        authenticateAs(43L);

        assertThat(run("POST").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousWrite_passesUntouched() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(run("POST").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse run(String method) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            chainCalls.incrementAndGet();
            ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_OK);
        };
        filter.doFilter(new MockHttpServletRequest(method, "/api/advertisements"), response, chain);
        return response;
    }

    private static void authenticateAs(Long actorId) {
        SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(actorId, null, List.of()));
    }
}
