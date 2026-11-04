package org.ost.integrationtests.level1.contact;

import org.junit.jupiter.api.Test;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test (no Spring context, no DB) — {@link ContactInfoDto#presentChannels} is pure field inspection with zero side effects. */
class ContactInfoDtoTest {

    @Test
    void presentChannels_allSet_returnsAllInFixedOrder() {
        ContactInfoDto dto = new ContactInfoDto(1L, EntityType.PROVIDER_PROFILE, 10L, "+123456789", "myhandle", "+987654321", null, null);

        List<Map.Entry<ContactChannel, String>> result = dto.presentChannels();

        assertThat(result).containsExactly(
                Map.entry(ContactChannel.PHONE, "+123456789"),
                Map.entry(ContactChannel.TELEGRAM, "myhandle"),
                Map.entry(ContactChannel.VIBER, "+987654321"));
    }

    @Test
    void presentChannels_allNull_returnsEmpty() {
        ContactInfoDto dto = new ContactInfoDto(1L, EntityType.PROVIDER_PROFILE, 10L, null, null, null, null, null);

        assertThat(dto.presentChannels()).isEmpty();
    }

    @Test
    void presentChannels_allBlank_returnsEmpty() {
        ContactInfoDto dto = new ContactInfoDto(1L, EntityType.PROVIDER_PROFILE, 10L, "  ", "  ", "  ", null, null);

        assertThat(dto.presentChannels()).isEmpty();
    }

    @Test
    void presentChannels_onlyPhoneSet_returnsOnlyPhone() {
        ContactInfoDto dto = new ContactInfoDto(1L, EntityType.PROVIDER_PROFILE, 10L, "+123456789", null, "  ", null, null);

        assertThat(dto.presentChannels()).containsExactly(
                Map.entry(ContactChannel.PHONE, "+123456789"));
    }
}
