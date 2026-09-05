package org.ost.integrationtests.level1.user;

import org.junit.jupiter.api.Test;
import org.ost.platform.user.dto.UserSettingsDto;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the builder-based deserialization applies Lombok's {@code @Builder.Default timelinePageSize} when the JSON payload omits that key. */
class UserSettingsDtoTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void deserialize_missingTimelinePageSizeKey_fallsBackToBuilderDefault() throws Exception {
        String json = """
                {"adsPageSize":20,"usersPageSize":20}
                """;

        UserSettingsDto settings = mapper.readValue(json, UserSettingsDto.class);

        assertThat(settings.getTimelinePageSize()).isEqualTo(20);
    }

    @Test
    void deserialize_allKeysPresent_usesProvidedValues() throws Exception {
        String json = """
                {"adsPageSize":30,"usersPageSize":40,"timelinePageSize":50}
                """;

        UserSettingsDto settings = mapper.readValue(json, UserSettingsDto.class);

        assertThat(settings.getAdsPageSize()).isEqualTo(30);
        assertThat(settings.getUsersPageSize()).isEqualTo(40);
        assertThat(settings.getTimelinePageSize()).isEqualTo(50);
    }
}
