package org.ost.mcptest;

import java.util.List;
import org.ost.mcp.config.McpProperties;
import org.ost.mcp.config.McpSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Test wiring for the security chain test; it lives outside {@code org.ost.mcp} so the module's component scan never picks it up. */
@Configuration
@EnableWebSecurity
@EnableWebMvc
@Import(McpSecurityConfig.class)
public class McpSecurityProbeConfig {

    @Bean
    McpProperties mcpProperties() {
        return new McpProperties("http://localhost:8080", List.of("http://localhost:*"));
    }

    @Bean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    /** Answers POST on both {@code /mcp} and a path outside it, so the test can tell which one the module's chain covers. */
    @RestController
    static class ProbeController {

        @PostMapping({"/mcp", "/not-mcp"})
        String ok() {
            return "ok";
        }

        @GetMapping("/not-mcp")
        String okGet() {
            return "ok";
        }
    }
}
