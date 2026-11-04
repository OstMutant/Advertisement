package org.ost.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Checks the origin decisions of {@link McpOriginFilter}: pass or continue, and 403 without continuing. */
class McpOriginFilterTest {

    private static final List<String> DEFAULTS = List.of("http://localhost:*", "http://127.0.0.1:*");

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private boolean chainContinued(List<String> allowed, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockFilterChain chain = new MockFilterChain();
        new McpOriginFilter(allowed).doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    @Test
    void requestWithoutOriginPasses() throws Exception {
        assertThat(chainContinued(DEFAULTS, null)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8081", "http://127.0.0.1:3000"})
    void defaultOriginsPass(String origin) throws Exception {
        assertThat(chainContinued(DEFAULTS, origin)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://evil.example", "http://localhost.evil.example"})
    void foreignOriginGets403AndStopsTheChain(String origin) throws Exception {
        assertThat(chainContinued(DEFAULTS, origin)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void configuredExtraOriginPasses() throws Exception {
        assertThat(chainContinued(List.of("https://app.example"), "https://app.example")).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
