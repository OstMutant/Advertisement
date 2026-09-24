package org.ost.platform.contact.spi;

import lombok.NonNull;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * Owns contact_info (phone/telegram/viber attached to a PROVIDER_PROFILE or ADVERTISEMENT) and
 * contact_view (append-only reveal/click events), generic over the owning entity. No knowledge of
 * provider-profile or advertisement internals. Implementation lives in contact-spring-boot-starter.
 */
public interface ContactPort {

    Optional<ContactInfoDto> find(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Upserts the {@code contact_info} row for {@code (entityType, entityId)}. {@code dto.version()}
     *  must be the value the caller last read (null when creating); a stale value throws
     *  {@link org.ost.platform.core.StaleWriteException}. */
    ContactInfoDto save(@NonNull ContactInfoDto dto);

    /** Records one reveal/click event. {@code viewerId} is null for anonymous reveals. */
    void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId);

    /** Per-channel reveal counts for the current calendar month. Channels with zero reveals are omitted. */
    List<ContactViewCountDto> countViewsThisMonth(@NonNull EntityType entityType, @NonNull Long entityId);
}
