package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application-level use case: save/delete a provider profile in one transaction, including its
 * category/city assignment, contact-info upsert, audit capture, and owner-or-privileged
 * authorization. 2 direct domain ports (ProviderProfile + Audit) plus the shared
 * {@link TaxonAssignmentWriteService}/{@link ContactAccessService} collaborators -- see
 * marketplace-orchestrator/CLAUDE.md's "≤2 domain *Port types per class" constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderProfileSaveService {

    private final TransactionTemplate                    tx;
    private final ComponentFactory<ProviderProfilePort>  providerProfilePortFactory;
    private final ComponentFactory<AuditPort>             auditPortFactory;
    private final TaxonAssignmentWriteService              taxonAssignmentWriteService;
    private final ContactAccessService                     contactAccessService;
    private final ProviderProfileDisplayEnrichmentService  displayEnrichmentService;
    private final CurrentLocaleService                     currentLocaleService;
    private final SitemapService                           sitemapService;
    private final AuthorizationService                     authorizationService;

    public Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long targetUserId, @NonNull Long actorId) {
        Long savedId = tx.execute(status -> {
            boolean isNew = dto.id() == null;
            ProviderProfileDto existing = isNew ? null : loadForEdit(dto.id());
            // An edit is authorized against the profile's real owner, never against the owner the caller names.
            authorizationService.requireCanOperate(actorId, isNew ? targetUserId : existing.getActorId());

            Long id = saveThroughPort(dto, targetUserId, actorId);

            Set<Long> catIds = dto.categoryIds() != null ? dto.categoryIds() : Set.of();
            taxonAssignmentWriteService.replace(EntityType.PROVIDER_PROFILE, id,
                    TaxonAssignmentWriteService.unionAssignmentIds(catIds, dto.cityTaxonId()));

            ContactInfoDto contact = saveContact(id, dto);

            captureAudit(isNew, id, snapshotAfterSave(findById(id).orElseThrow(), contact), actorId);
            log.info("ProviderProfile save transaction complete: id={}, isNew={}, categories={}",
                    id, isNew, catIds.size());
            return id;
        });
        sitemapService.invalidate();
        return savedId;
    }

    private ProviderProfileDto loadForEdit(Long id) {
        ProviderProfileDto existing = fetchEnriched(id);
        if (existing == null) {
            throw new StaleWriteException("Provider profile " + id + " was deleted before this edit could be saved");
        }
        return existing;
    }

    private Long saveThroughPort(ProviderProfileSaveDto dto, Long targetUserId, Long actorId) {
        boolean privileged = authorizationService.isPrivileged(actorId);
        try {
            return providerProfilePortFactory.get().save(dto, targetUserId, actorId, privileged);
        } catch (IllegalStateException ex) {
            // Starter's invariant check reads as authorization to any caller -- translate to 403 here.
            throw new AccessDeniedException(ex.getMessage());
        }
    }

    private static ProviderProfileSnapshotDto snapshotAfterSave(ProviderProfileDto saved, ContactInfoDto contact) {
        return new ProviderProfileSnapshotDto(
                saved.getKind(), saved.getAbout(), sortedList(saved.getCategoryIds()), saved.getCityTaxonId(),
                contact != null ? contact.phone() : null,
                contact != null ? contact.telegram() : null,
                contact != null ? contact.viber() : null);
    }

    private void captureAudit(boolean isNew, Long savedId, ProviderProfileSnapshotDto after, Long actorId) {
        if (isNew) {
            auditPortFactory.ifAvailable(p -> p.captureCreation(savedId, after, actorId));
        } else {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(savedId, after, actorId));
        }
    }

    public boolean isAvailable() {
        return providerProfilePortFactory.findIfAvailable().isPresent();
    }

    private ProviderProfileDto enrich(ProviderProfileDto p) {
        return displayEnrichmentService.enrichWithCategoryAndCity(p, currentLocaleService.getCurrentLocale());
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return providerProfilePortFactory.get().findById(id).map(this::enrich);
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return providerProfilePortFactory.get().findByActorId(actorId).map(this::enrich);
    }

    public void delete(@NonNull Long id, @NonNull Long actorId, Long version) {
        tx.executeWithoutResult(status -> {
            ProviderProfileDto existing = fetchEnriched(id);
            ProviderProfileSnapshotDto snapshot = existing == null ? null : toSnapshot(existing);
            if (snapshot != null) {
                authorizationService.requireCanOperate(actorId, existing.getActorId());
                taxonAssignmentWriteService.clear(EntityType.PROVIDER_PROFILE, id);
            }
            providerProfilePortFactory.get().delete(id, version);
            contactAccessService.delete(EntityType.PROVIDER_PROFILE, id);
            if (snapshot != null) {
                auditPortFactory.ifAvailable(p -> p.captureDeletion(id, snapshot, actorId));
            }
        });
        sitemapService.invalidate();
    }

    private ProviderProfileDto fetchEnriched(@NonNull Long entityId) {
        return findById(entityId).orElse(null);
    }

    private ProviderProfileSnapshotDto toSnapshot(@NonNull ProviderProfileDto p) {
        Optional<ContactInfoDto> contact = contactAccessService.find(EntityType.PROVIDER_PROFILE, p.getId());
        return new ProviderProfileSnapshotDto(p.getKind(), p.getAbout(), sortedList(p.getCategoryIds()), p.getCityTaxonId(),
                contact.map(ContactInfoDto::phone).orElse(null),
                contact.map(ContactInfoDto::telegram).orElse(null),
                contact.map(ContactInfoDto::viber).orElse(null));
    }

    /** Upserts contact_info as part of the same transaction, keyed fresh by (entityType, entityId) -- returns null (skips the write) when neither an existing row nor any new field value exists. */
    private ContactInfoDto saveContact(@NonNull Long profileId, @NonNull ProviderProfileSaveDto dto) {
        Optional<ContactInfoDto> existing = contactAccessService.find(EntityType.PROVIDER_PROFILE, profileId);
        boolean anyFieldSet = dto.phone() != null || dto.telegram() != null || dto.viber() != null;
        if (existing.isEmpty() && !anyFieldSet) return null;
        return contactAccessService.save(new ContactInfoDto(
                existing.map(ContactInfoDto::id).orElse(null),
                EntityType.PROVIDER_PROFILE, profileId,
                dto.phone(), dto.telegram(), dto.viber(),
                null, existing.map(ContactInfoDto::version).orElse(null)));
    }

    private static List<Long> sortedList(Set<Long> ids) {
        return ids != null ? ids.stream().sorted().toList() : List.of();
    }
}
