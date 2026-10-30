package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: the per-account write limit on the bearer chain answers 429 on the 61st write in a minute while reads and other accounts pass. */
@Level3ScenarioTest
class ApiWriteRateLimitScenarioTest extends AbstractRestApiScenarioTest {

    private static final int WRITES_PER_MINUTE = 60;

    @Test
    void sixtyFirstWriteInAMinute_isTooManyRequests_whileReadsAndOtherAccountsPass() throws Exception {
        RegisteredUser writer = registerUserAndIssueApiKey("Heavy Writer");
        String bearer = "Bearer " + writer.rawApiKey();

        for (int write = 1; write <= WRITES_PER_MINUTE; write++) {
            mockMvc.perform(delete("/api/advertisements/" + Long.MAX_VALUE).header("If-Match", "\"0\"")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().is(not(429)));
        }

        mockMvc.perform(delete("/api/advertisements/" + Long.MAX_VALUE).header("If-Match", "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.message", containsString("Too many write requests")));
        mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        RegisteredUser other = registerUserAndIssueApiKey("Light Writer");
        mockMvc.perform(delete("/api/advertisements/" + Long.MAX_VALUE).header("If-Match", "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.rawApiKey()))
                .andExpect(status().is(not(429)));
    }
}
