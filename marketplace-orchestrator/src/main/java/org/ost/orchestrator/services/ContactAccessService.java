package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Wraps {@link ContactPort} for marketplace-app; an advertisement shows its owner's PROVIDER_PROFILE contact, looked up via {@link AdvertisementOwnerProfileLookupService}. */
@Service
@RequiredArgsConstructor
public class ContactAccessService {

    private final ComponentFactory<ContactPort> contactPortFactory;
    private final AdvertisementOwnerProfileLookupService advertisementOwnerProfileLookupService;
    private final AuthorizationService authorizationService;
    private final ProviderProfileReadService providerProfileReadService;

    public Optional<ContactInfoDto> find(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactPortFactory.findIfAvailable().flatMap(port -> port.find(entityType, entityId));
    }

    /** A provider profile's own contact, or an advertisement's owner's provider-profile contact; any other entity type is rejected. */
    public Optional<ContactInfoDto> resolveContact(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactPortFactory.findIfAvailable().flatMap(port -> switch (entityType) {
            case PROVIDER_PROFILE -> port.find(EntityType.PROVIDER_PROFILE, entityId);
            case ADVERTISEMENT -> advertisementOwnerProfileLookupService.findOwnerProfileId(entityId)
                    .flatMap(profileId -> port.find(EntityType.PROVIDER_PROFILE, profileId));
            default -> throw new IllegalArgumentException("No contact for entity type " + entityType);
        });
    }

    public ContactInfoDto save(@NonNull ContactInfoDto dto) {
        return contactPortFactory.get().save(dto);
    }

    public void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId, String revealedValue) {
        contactPortFactory.ifAvailable(port -> port.recordView(entityType, entityId, channel, viewerId, revealedValue));
    }

    /** Only the owner of the contact's entity, an administrator or a moderator may read the view counts. */
    public List<ContactViewCountDto> countViewsThisMonth(
            @NonNull Long actingUserId, @NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long ownerId) {
        authorizationService.requireCanOperate(actingUserId, ownerId);
        return contactPortFactory.findIfAvailable()
                .map(port -> port.countViewsThisMonth(entityType, entityId))
                .orElse(List.of());
    }

    /** This month's reveal counts of a provider profile, looked up by profile id; only its owner, an administrator or a moderator may read them. */
    public List<ContactViewCountDto> countProfileViewsThisMonth(@NonNull Long actingUserId, @NonNull Long profileId) {
        Long ownerId = providerProfileReadService.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Provider profile " + profileId + " not found"))
                .getActorId();
        return countViewsThisMonth(actingUserId, EntityType.PROVIDER_PROFILE, profileId, ownerId);
    }

    public void delete(@NonNull EntityType entityType, @NonNull Long entityId) {
        contactPortFactory.ifAvailable(port -> port.delete(entityType, entityId));
    }

    public boolean isAvailable() {
        return contactPortFactory.findIfAvailable().isPresent();
    }
}
