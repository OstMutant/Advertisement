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
import java.util.Optional;

/** Wraps {@link ContactPort} for marketplace-app, adding the ADVERTISEMENT-to-owner's-PROVIDER_PROFILE contact fallback via {@link AdvertisementOwnerProfileLookupService}. */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ComponentFactory<ContactPort> contactPortFactory;
    private final AdvertisementOwnerProfileLookupService advertisementOwnerProfileLookupService;

    public Optional<ContactInfoDto> find(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactPortFactory.findIfAvailable().flatMap(port -> port.find(entityType, entityId));
    }

    public Optional<ContactInfoDto> resolveContact(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactPortFactory.findIfAvailable().flatMap(port -> {
            Optional<ContactInfoDto> own = port.find(entityType, entityId);
            if (own.isPresent() || entityType != EntityType.ADVERTISEMENT) {
                return own;
            }
            return advertisementOwnerProfileLookupService.findOwnerProfileId(entityId)
                    .flatMap(profileId -> port.find(EntityType.PROVIDER_PROFILE, profileId));
        });
    }

    public ContactInfoDto save(@NonNull ContactInfoDto dto) {
        return contactPortFactory.get().save(dto);
    }

    public void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId) {
        contactPortFactory.ifAvailable(port -> port.recordView(entityType, entityId, channel, viewerId));
    }

    public List<ContactViewCountDto> countViewsThisMonth(@NonNull EntityType entityType, @NonNull Long entityId) {
        return contactPortFactory.findIfAvailable()
                .map(port -> port.countViewsThisMonth(entityType, entityId))
                .orElse(List.of());
    }

    public boolean isAvailable() {
        return contactPortFactory.findIfAvailable().isPresent();
    }
}
