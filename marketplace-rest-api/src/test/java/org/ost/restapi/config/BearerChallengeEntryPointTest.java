package org.ost.restapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link BearerChallengeEntryPoint} answers 401 with a Bearer challenge only, adding the invalid-token error when a bearer header was sent. */
class BearerChallengeEntryPointTest {

    private final BearerChallengeEntryPoint entryPoint = new BearerChallengeEntryPoint();

    @Test
    void noAuthorizationHeader_getsBareBearerChallenge() throws Exception {
        MockHttpServletResponse response = commence(new MockHttpServletRequest());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"api\"");
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Authentication required.\"}");
    }

    @Test
    void bearerHeaderThatFailed_getsInvalidTokenError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-key");

        assertThat(commence(request).getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"api\", error=\"invalid_token\"");
    }

    @Test
    void basicHeader_getsNoBasicChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        assertThat(commence(request).getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"api\"");
    }

    private MockHttpServletResponse commence(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new InsufficientAuthenticationException("test"));
        return response;
    }
}
