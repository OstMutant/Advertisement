package org.ost.mcptest;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/** Provides the web-security infrastructure a plain auto-configuration test lacks; it lives outside {@code org.ost.mcp} so the module's component scan never picks it up. */
@Configuration
@EnableWebSecurity
public class McpSecurityEnabler {
}
