package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SupportedLocales;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Returns an entity to a recorded snapshot by saving the snapshot's values through the domain's normal save path,
 * so validation, authorization and audit capture are exactly those of an ordinary update.
 */
@Service
@RequiredArgsConstructor
public class SnapshotRestoreService {

    private final AuditQueryService auditQueryService;
    private final AdvertisementSaveService advertisementSaveService;
    private final ProviderProfileSaveService providerProfileSaveService;
    private final ProviderProfileReadService providerProfileReadService;
    private final UserProfileService userProfileService;
    private final TaxonCatalogService taxonCatalogService;
    private final AttachmentMediaService attachmentMediaService;
    private final SnapshotMapper snapshotMapper;

    /** Restores title, description, kind, categories and city, and the gallery when the snapshot recorded one. */
    public Long restoreAdvertisement(@NonNull Long actingUserId, @NonNull Long adId, @NonNull Long snapshotId, Long version) {
        AdvertisementSnapshotDto snapshot = read(actingUserId, EntityType.ADVERTISEMENT, adId, snapshotId, AdvertisementSnapshotDto.class);
        return advertisementSaveService.save(snapshotMapper.toSaveDto(snapshot, adId, version), actingUserId,
                ref -> restoreGallery(ref, snapshot.attachmentSnapshotId()));
    }

    /** Restores kind, about, categories, city and the three contact channels, saved against the profile's real owner. */
    public Long restoreProviderProfile(@NonNull Long actingUserId, @NonNull Long profileId, @NonNull Long snapshotId, Long version) {
        ProviderProfileSnapshotDto snapshot = read(actingUserId, EntityType.PROVIDER_PROFILE, profileId, snapshotId, ProviderProfileSnapshotDto.class);
        Long ownerId = providerProfileReadService.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Provider profile " + profileId + " not found"))
                .getActorId();
        return providerProfileSaveService.save(snapshotMapper.toSaveDto(snapshot, profileId, version), ownerId, actingUserId);
    }

    /** Restores name and role only: the save path cannot change an email, so the snapshot's email is ignored. */
    public void restoreUser(@NonNull Long actingUserId, @NonNull Long userId, @NonNull Long snapshotId, Long version) {
        UserSnapshotDto snapshot = read(actingUserId, EntityType.USER, userId, snapshotId, UserSnapshotDto.class);
        userProfileService.save(new UserProfileDto(userId, snapshot.name(), Role.valueOf(snapshot.role()), version), actingUserId);
    }

    /** Restores the six page-size settings of the user whose settings the snapshot belongs to. */
    public void restoreSettings(@NonNull Long actingUserId, @NonNull Long userId, @NonNull Long snapshotId, Long version) {
        SettingsSnapshotDto snapshot = read(actingUserId, EntityType.USER_SETTINGS, userId, snapshotId, SettingsSnapshotDto.class);
        userProfileService.saveSettings(actingUserId, userId, snapshotMapper.toSettings(snapshot, version));
    }

    /** Restores the English and Ukrainian names and descriptions. */
    public void restoreTaxon(@NonNull Long actingUserId, @NonNull Long taxonId, @NonNull Long snapshotId, Long version) {
        TaxonSnapshotDto snapshot = read(actingUserId, EntityType.TAXON, taxonId, snapshotId, TaxonSnapshotDto.class);
        Map<Locale, TaxonTranslationDto> translations = Map.of(
                Locale.forLanguageTag(SupportedLocales.ENGLISH_TAG), translation(SupportedLocales.ENGLISH_TAG, snapshot.nameEn(), snapshot.descriptionEn()),
                Locale.forLanguageTag(SupportedLocales.UKRAINIAN_TAG), translation(SupportedLocales.UKRAINIAN_TAG, snapshot.nameUk(), snapshot.descriptionUk()));
        taxonCatalogService.update(taxonId, translations, actingUserId, version);
    }

    // A snapshot that recorded no gallery leaves the gallery as it is, as the form's restore does.
    private Long restoreGallery(EntityRef ref, Long attachmentSnapshotId) {
        if (attachmentSnapshotId == null) {
            return null;
        }
        attachmentMediaService.restoreToUrls(ref.entityType(), ref.entityId(), attachmentMediaService.getUrlsBySnapshotId(attachmentSnapshotId));
        attachmentMediaService.captureSnapshot(ref.entityType(), ref.entityId());
        return attachmentMediaService.getLatestSnapshotId(ref.entityType(), ref.entityId());
    }

    private <T extends AuditableSnapshot> T read(Long actingUserId, EntityType entityType, Long entityId, Long snapshotId, Class<T> type) {
        return auditQueryService.getSnapshotContent(actingUserId, entityId, snapshotId, entityType, type)
                .orElseThrow(() -> new NoSuchElementException("Snapshot " + snapshotId + " not found"))
                .snapshotData();
    }

    private static TaxonTranslationDto translation(String locale, String name, String description) {
        return TaxonTranslationDto.builder().locale(locale)
                .name(Objects.requireNonNullElse(name, "")).description(Objects.requireNonNullElse(description, "")).build();
    }
}
