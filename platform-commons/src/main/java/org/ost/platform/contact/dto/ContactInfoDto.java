package org.ost.platform.contact.dto;

import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The phone/telegram/viber contact data attached to one owning entity (PROVIDER_PROFILE or ADVERTISEMENT). */
@FieldNameConstants
public record ContactInfoDto(
        Long id,
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @Pattern(regexp = PHONE_PATTERN) String phone,
        @Pattern(regexp = TELEGRAM_PATTERN) String telegram,
        @Pattern(regexp = PHONE_PATTERN) String viber,
        Instant updatedAt,
        Long version
) {
    public static final String PHONE_PATTERN    = "^\\+[1-9]\\d{1,14}$";
    public static final String TELEGRAM_PATTERN = "^[A-Za-z0-9_]{5,32}$";

    /** Non-blank (channel, value) pairs in fixed PHONE/TELEGRAM/VIBER order, for callers rendering one row per set channel. */
    public List<Map.Entry<ContactChannel, String>> presentChannels() {
        List<Map.Entry<ContactChannel, String>> result = new ArrayList<>();
        if (!isBlank(phone)) result.add(Map.entry(ContactChannel.PHONE, phone));
        if (!isBlank(telegram)) result.add(Map.entry(ContactChannel.TELEGRAM, telegram));
        if (!isBlank(viber)) result.add(Map.entry(ContactChannel.VIBER, viber));
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
