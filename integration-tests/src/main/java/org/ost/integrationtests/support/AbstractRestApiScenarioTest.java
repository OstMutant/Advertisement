package org.ost.integrationtests.support;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.ost.attachment.services.StorageService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared Level 3 scaffolding: real security-chain MockMvc, DB cleanup, S3 stubs, and a register-user-plus-issue-key step every scenario starts with. */
public abstract class AbstractRestApiScenarioTest extends AbstractPostgresIntegrationTest {

    @MockitoBean protected S3Client s3Client;
    @MockitoBean protected StorageService storageService;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcClient jdbcClient;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter springSecurityFilterChain;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpRestApiScenario() {
        TestDataCleaner.cleanAll(jdbcClient);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(springSecurityFilterChain).build();
    }

    protected record RegisteredUser(long id, String email, String password, String rawApiKey) {
    }

    protected RegisteredUser registerUserAndIssueApiKey(String namePrefix) throws Exception {
        String email = namePrefix.toLowerCase().replace(" ", "") + "-" + System.nanoTime() + "@example.com";
        String password = "password123";
        String registerBody = """
                {"name":"%s","email":"%s","password":"%s"}""".formatted(namePrefix, email, password);
        String registerResponse = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = JsonScenarioUtils.extractId(registerResponse);

        String basicAuth = Base64.getEncoder().encodeToString((email + ":" + password).getBytes());
        String apiKeyResponse = mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rawKey = JsonScenarioUtils.extractStringField(apiKeyResponse, "rawKey");
        return new RegisteredUser(userId, email, password, rawKey);
    }
}
