package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: registering the same email twice against a real unique index -- confirms ApiExceptionHandler's DuplicateKeyException mapping end to end, not just against a mocked throw. */
@Level3ScenarioTest
class UserRegistrationScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void duplicateEmail_secondRegistration_getsRealConflict() throws Exception {
        String email = "duplicate-" + System.nanoTime() + "@example.com";
        String body = """
                {"name":"First User","email":"%s","password":"password123"}""".formatted(email);

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String secondBody = """
                {"name":"Second User","email":"%s","password":"password456"}""".formatted(email);
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isConflict());
    }
}
