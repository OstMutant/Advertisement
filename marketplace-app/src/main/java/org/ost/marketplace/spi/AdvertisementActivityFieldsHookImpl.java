package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdvertisementActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final I18nService i18n;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
    }

    @Override
    public List<ChangeEntry> expandFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return item.expandedChanges();
    }

    @Override
    public String labelFor(@NonNull String rawFieldKey) {
        return switch (rawFieldKey) {
            case AdvertisementSnapshotDto.Fields.title       -> i18n.get(I18nKey.CHANGES_FIELD_TITLE);
            case AdvertisementSnapshotDto.Fields.description -> i18n.get(I18nKey.CHANGES_FIELD_DESCRIPTION);
            case AdvertisementSnapshotDto.Fields.categoryIds -> i18n.get(I18nKey.CHANGES_FIELD_CATEGORY);
            default                                          -> rawFieldKey;
        };
    }
}
