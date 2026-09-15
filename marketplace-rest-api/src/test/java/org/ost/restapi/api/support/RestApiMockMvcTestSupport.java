package org.ost.restapi.api.support;

import lombok.extern.slf4j.Slf4j;
import org.ost.restapi.api.error.ApiExceptionHandler;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Shared standalone-{@link MockMvc} scaffolding for every REST controller contract test. */
@Slf4j
public final class RestApiMockMvcTestSupport {

    private RestApiMockMvcTestSupport() {
    }

    public static MockMvc mockMvc(Object... controllers) {
        // Real java.time support is built into Jackson 3's ObjectMapper, no module registration needed.
        JsonMapper objectMapper = JsonMapper.builder().build();
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .alwaysDo(CURL_LOG)
                .build();
    }

    /** Prints every request as a {@code curl} command plus its response status/body below it. */
    private static final ResultHandler CURL_LOG = result -> {
        MockHttpServletRequest request = result.getRequest();
        StringBuilder curl = new StringBuilder("curl -X ").append(request.getMethod()).append(" '").append(request.getRequestURL());
        if (request.getQueryString() != null) {
            curl.append('?').append(request.getQueryString());
        }
        curl.append('\'');
        Collections.list(request.getHeaderNames()).forEach(name ->
                curl.append(" -H '").append(name).append(": ").append(request.getHeader(name)).append('\''));
        byte[] body = request.getContentAsByteArray();
        if (body != null && body.length > 0) {
            curl.append(" -d '").append(new String(body, StandardCharsets.UTF_8)).append('\'');
        }
        String responseBody = result.getResponse().getContentAsString();
        log.info("{}\n< HTTP {}{}", curl, result.getResponse().getStatus(), responseBody.isEmpty() ? "" : "\n" + responseBody);
    };

    /** Pushes a resolved bearer-key authentication onto the security context, same shape {@link org.ost.restapi.config.ApiKeyAuthenticationFilter} builds in production. */
    public static void authenticateAs(Long actorId) {
        SecurityContextHolder.getContext().setAuthentication(
                new PreAuthenticatedAuthenticationToken(actorId, null, List.of()));
    }

    /** Pushes a resolved Basic-auth authentication carrying the given principal onto the security context. */
    public static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new PreAuthenticatedAuthenticationToken(principal, null, List.of()));
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
