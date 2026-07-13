package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaxonActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final I18nService i18n;

    @Override
    public EntityType entityType() {
        return EntityType.TAXON;
    }

    @Override
    public List<ChangeEntry> expandFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return item.snapshotData() != null
                ? item.snapshotData().expandWithChanges(item.changes())
                : item.changes();
    }

    @Override
    public String labelFor(@NonNull String rawFieldKey) {
        return switch (rawFieldKey) {
            case TaxonSnapshotDto.Fields.nameEn          -> i18n.get(I18nKey.CHANGES_FIELD_NAME_EN);
            case TaxonSnapshotDto.Fields.descriptionEn   -> i18n.get(I18nKey.CHANGES_FIELD_DESCRIPTION_EN);
            case TaxonSnapshotDto.Fields.nameUk          -> i18n.get(I18nKey.CHANGES_FIELD_NAME_UK);
            case TaxonSnapshotDto.Fields.descriptionUk   -> i18n.get(I18nKey.CHANGES_FIELD_DESCRIPTION_UK);
            default                                      -> rawFieldKey;
        };
    }
}
