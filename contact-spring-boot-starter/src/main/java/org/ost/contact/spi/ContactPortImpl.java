package org.ost.contact.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.contact.services.ContactService;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Pure delegation to {@link ContactService} -- no business logic of its own, per this project's
 *  {@code *PortImpl} convention. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactPortImpl implements ContactPort {

    private final ContactService service;

    @Override
    public Optional<ContactInfoDto> find(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.find(entityType, entityId);
    }

    @Override
    @Transactional
    public ContactInfoDto save(@NonNull ContactInfoDto dto) {
        return service.save(dto);
    }

    @Override
    @Transactional
    public void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId) {
        service.recordView(entityType, entityId, channel, viewerId);
    }

    @Override
    public List<ContactViewCountDto> countViewsThisMonth(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.countViewsThisMonth(entityType, entityId);
    }
}
