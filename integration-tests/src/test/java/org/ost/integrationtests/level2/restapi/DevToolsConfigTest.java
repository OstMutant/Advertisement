package org.ost.integrationtests.level2.restapi;

import org.junit.jupiter.api.Test;
import org.ost.restapi.config.DevToolsConfig;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The REST and MCP flow runner pages and their Postman collections are served only when {@code marketplace.dev-tools.enabled} is true, and are absent (404) otherwise. */
class DevToolsConfigTest {

    private static final String PAGE = "/dev-tools/api-flows/index.html";
    private static final String COLLECTION = "/dev-tools/api-flows/postman/marketplace-rest-api.postman_collection.json";
    private static final String MCP_PAGE = "/dev-tools/api-flows/mcp.html";
    private static final String MCP_COLLECTION = "/dev-tools/api-flows/postman/marketplace-mcp.postman_collection.json";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(MvcConfig.class, DevToolsConfig.class);

    @Test
    void propertyUnset_pageAndCollectionAreNotFound() {
        runner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get(PAGE)).andExpect(status().isNotFound());
            mockMvc.perform(get(COLLECTION)).andExpect(status().isNotFound());
            mockMvc.perform(get(MCP_PAGE)).andExpect(status().isNotFound());
            mockMvc.perform(get(MCP_COLLECTION)).andExpect(status().isNotFound());
            mockMvc.perform(get("/dev-tools/api-flows/")).andExpect(status().isNotFound());
        });
    }

    @Test
    void propertyFalse_pageIsNotFound() {
        runner.withPropertyValues("marketplace.dev-tools.enabled=false").run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get(PAGE)).andExpect(status().isNotFound());
            mockMvc.perform(get(MCP_PAGE)).andExpect(status().isNotFound());
            mockMvc.perform(get(MCP_COLLECTION)).andExpect(status().isNotFound());
        });
    }

    @Test
    void propertyTrue_pageAndCollectionAreServed() {
        runner.withPropertyValues("marketplace.dev-tools.enabled=true").run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get(PAGE)).andExpect(status().isOk()).andExpect(content().string(containsString("API flow runner")));
            mockMvc.perform(get(COLLECTION)).andExpect(status().isOk()).andExpect(content().string(containsString("\"info\"")));
            mockMvc.perform(get(MCP_PAGE)).andExpect(status().isOk()).andExpect(content().string(containsString("Postman collection (MCP)")));
            mockMvc.perform(get(MCP_COLLECTION)).andExpect(status().isOk()).andExpect(content().string(containsString("\"info\"")));
            mockMvc.perform(get("/dev-tools/api-flows/")).andExpect(forwardedUrl(PAGE));
        });
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig {
    }
}
