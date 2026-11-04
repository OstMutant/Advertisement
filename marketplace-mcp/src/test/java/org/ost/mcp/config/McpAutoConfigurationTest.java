package org.ost.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.ost.mcptest.McpSecurityEnabler;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks that the auto-configuration context starts and binds marketplace.mcp.rest-base-url, both its default and an override. */
class McpAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, McpAutoConfiguration.class))
            .withUserConfiguration(McpSecurityEnabler.class);

    @Test
    void contextStartsWithDefaultRestBaseUrl() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(McpProperties.class);
            assertThat(context.getBean(McpProperties.class).restBaseUrl()).isEqualTo("http://localhost:8080");
        });
    }

    @Test
    void restBaseUrlIsOverridable() {
        runner.withPropertyValues("marketplace.mcp.rest-base-url=http://example:1")
                .run(context -> assertThat(context.getBean(McpProperties.class).restBaseUrl())
                        .isEqualTo("http://example:1"));
    }
}
