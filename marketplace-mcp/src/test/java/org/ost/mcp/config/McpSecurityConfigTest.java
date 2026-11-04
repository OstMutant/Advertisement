package org.ost.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.mcptest.McpSecurityProbeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Runs the real {@link McpSecurityConfig} chain next to a CSRF-protected default chain to prove only {@code POST /mcp} skips CSRF. */
@SpringJUnitWebConfig(McpSecurityProbeConfig.class)
class McpSecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void postToMcpWithoutCsrfTokenPassesTheChain() throws Exception {
        assertThat(mockMvc.perform(post("/mcp")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void postToOtherPathWithoutCsrfTokenIsRejected() throws Exception {
        assertThat(mockMvc.perform(post("/not-mcp")).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void foreignOriginOnMcpIsRejected() throws Exception {
        assertThat(mockMvc.perform(post("/mcp").header("Origin", "http://evil.example")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void allowedOriginOnMcpPasses() throws Exception {
        assertThat(mockMvc.perform(post("/mcp").header("Origin", "http://localhost:8081")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void originOutsideMcpIsNotChecked() throws Exception {
        assertThat(mockMvc.perform(get("/not-mcp").header("Origin", "http://evil.example")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }
}
