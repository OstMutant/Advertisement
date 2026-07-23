package org.ost.integrationtests.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ost.platform.user.dto.UserSettingsDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-050 item 5's "Required verification": does Jackson's builder-based
 * deserialization ({@code @JsonDeserialize(builder = UserSettingsDtoBuilder.class)}) actually
 * apply Lombok's {@code @Builder.Default timelinePageSize = 20} when the JSON payload is missing
 * the {@code timelinePageSize} key entirely (the exact shape of the Liquibase-seeded {@code
 * settings} column default, which predates the field and was never updated to include it) — or
 * does it silently fall back to {@code 0}?
 *
 * <p>No Spring context needed — plain {@code ObjectMapper}, same behavior as the real
 * {@code userSettingsObjectMapper} bean (marketplace-app's {@code JacksonConfig}) for this
 * specific question; that bean only adds {@code JavaTimeModule} and disables two unrelated
 * features, neither of which affects builder-default fallback.</p>
 */
class UserSettingsDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

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
