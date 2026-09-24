package org.ost.contact.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.contact.entity.ContactInfo;
import org.ost.contact.repository.ContactRepository;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

/** CRUD for {@code contact_info}, append-only writes and aggregate reads for {@code contact_view}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class ContactService {

    private final ContactRepository repository;

    public Optional<ContactInfoDto> find(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.findByEntity(entityType, entityId).map(ContactService::toDto);
    }

    @Transactional
    public ContactInfoDto save(@NonNull @Valid ContactInfoDto dto) {
        Optional<ContactInfo> before = repository.findByEntity(dto.entityType(), dto.entityId());
        log.info("ContactInfo save: entityType={}, entityId={}, isNew={}", dto.entityType(), dto.entityId(), before.isEmpty());
        ContactInfo entity = buildEntity(dto, before.orElse(null));
        return toDto(repository.save(entity));
    }

    @Transactional
    public void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId) {
        repository.recordView(entityType, entityId, channel, viewerId);
    }

    public List<ContactViewCountDto> countViewsThisMonth(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.countViewsThisMonth(entityType, entityId);
    }

    private static ContactInfo buildEntity(ContactInfoDto dto, ContactInfo before) {
        return ContactInfo.builder()
                .id(before != null ? before.getId() : null)
                .entityType(dto.entityType())
                .entityId(dto.entityId())
                .phone(dto.phone())
                .telegram(dto.telegram())
                .viber(dto.viber())
                .createdAt(before != null ? before.getCreatedAt() : null)
                .version(dto.version())
                .build();
    }

    private static ContactInfoDto toDto(ContactInfo entity) {
        return new ContactInfoDto(entity.getId(), entity.getEntityType(), entity.getEntityId(),
                entity.getPhone(), entity.getTelegram(), entity.getViber(),
                entity.getUpdatedAt(), entity.getVersion());
    }
}
