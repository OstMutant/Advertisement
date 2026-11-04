package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link BasicLoginRateLimitFilter} counts failed Basic logins per client address and email, and blocks with 429 before the credentials are checked again. */
class BasicLoginRateLimitFilterTest {

    private BasicLoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BasicLoginRateLimitFilter();
    }

    @Test
    void requestWithoutBasicHeader_passesThroughUntouched() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest(), HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    void malformedBasicHeader_passesThroughUntouched() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(run(withAuthorization("Basic !!!not-base64!!!"), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
        }
        assertThat(run(withAuthorization(basic("no-colon-here")), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
    }

    @Test
    void sixthFailedLogin_isBlockedWith429AndRetryAfter() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse blocked = run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        assertThat(blocked.getContentAsString()).isEqualTo("{\"message\":\"Too many failed login attempts, try again later\"}");
    }

    @Test
    void blockedCaller_isNotLetThroughEvenWithGoodCredentials() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);
        }

        assertThat(run(login("a@example.com"), HttpServletResponse.SC_CREATED).getStatus()).isEqualTo(429);
    }

    @Test
    void successfulLogin_clearsTheCounter() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);
        }
        run(login("a@example.com"), HttpServletResponse.SC_CREATED);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
        }
    }

    @Test
    void anotherEmail_hasItsOwnCounter() throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);
        }

        assertThat(run(login("b@example.com"), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
    }

    @Test
    void anotherAddress_hasItsOwnCounter() throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);
        }
        MockHttpServletRequest fromElsewhere = login("a@example.com");
        fromElsewhere.setRemoteAddr("203.0.113.9");

        assertThat(run(fromElsewhere, HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(401);
    }

    @Test
    void emailCaseAndSpaces_doNotCreateANewCounter() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            run(login("a@example.com"), HttpServletResponse.SC_UNAUTHORIZED);
        }

        assertThat(run(login("  A@Example.COM "), HttpServletResponse.SC_UNAUTHORIZED).getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, int downstreamStatus) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(downstreamStatus);
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest login(String email) {
        return withAuthorization(basic(email + ":secret"));
    }

    private static MockHttpServletRequest withAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader(HttpHeaders.AUTHORIZATION, value);
        return request;
    }

    private static String basic(String userAndPassword) {
        return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
    }
}
