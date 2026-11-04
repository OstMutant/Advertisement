package org.ost.restapi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** Answers an unauthenticated {@code /api/**} request with 401 and a Bearer challenge (RFC 6750); a Basic challenge would make browsers open their login prompt. */
public class BearerChallengeEntryPoint implements AuthenticationEntryPoint {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHALLENGE = "Bearer realm=\"api\"";
    private static final String BODY = "{\"message\":\"Authentication required.\"}";

    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull AuthenticationException authException)
            throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean bearerSent = authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, bearerSent ? CHALLENGE + ", error=\"invalid_token\"" : CHALLENGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(BODY);
    }
}
