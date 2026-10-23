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

/** The flow runner page and its Postman collection are served only when {@code marketplace.dev-tools.enabled} is true, and are absent (404) otherwise. */
class DevToolsConfigTest {

    private static final String PAGE = "/dev-tools/api-flows/index.html";
    private static final String COLLECTION = "/dev-tools/api-flows/postman/marketplace-rest-api.postman_collection.json";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(MvcConfig.class, DevToolsConfig.class);

    @Test
    void propertyUnset_pageAndCollectionAreNotFound() {
        runner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get(PAGE)).andExpect(status().isNotFound());
            mockMvc.perform(get(COLLECTION)).andExpect(status().isNotFound());
            mockMvc.perform(get("/dev-tools/api-flows/")).andExpect(status().isNotFound());
        });
    }

    @Test
    void propertyFalse_pageIsNotFound() {
        runner.withPropertyValues("marketplace.dev-tools.enabled=false").run(context ->
                MockMvcBuilders.webAppContextSetup(context).build()
                        .perform(get(PAGE)).andExpect(status().isNotFound()));
    }

    @Test
    void propertyTrue_pageAndCollectionAreServed() {
        runner.withPropertyValues("marketplace.dev-tools.enabled=true").run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get(PAGE)).andExpect(status().isOk()).andExpect(content().string(containsString("API flow runner")));
            mockMvc.perform(get(COLLECTION)).andExpect(status().isOk()).andExpect(content().string(containsString("\"info\"")));
            mockMvc.perform(get("/dev-tools/api-flows/")).andExpect(forwardedUrl(PAGE));
        });
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig {
    }
}
