package org.ost.marketplace.spi;

import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.ActivityFieldsHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdvertisementActivityFieldsHookImpl implements ActivityFieldsHook {

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.ADVERTISEMENT;
    }

    @Override
    public List<ChangeEntry> expandFields(AuditActivityItemDto item) {
        if (!(item.snapshotData() instanceof AdvertisementSnapshotDto state)) return item.changes();
        List<ChangeEntry> result = new ArrayList<>();
        addField(result, item.changes(), "title",       I18nKey.CHANGES_FIELD_TITLE,       state.title());
        addField(result, item.changes(), "description", I18nKey.CHANGES_FIELD_DESCRIPTION, state.description());
        return result;
    }

    private static void addField(List<ChangeEntry> result, List<ChangeEntry> changes,
                                  String field, I18nKey labelKey, String currentValue) {
        ChangeEntry existing = changes.stream()
                .filter(c -> c instanceof ChangeEntry.FieldChange fc && field.equals(fc.field()))
                .findFirst().orElse(null);
        if (existing instanceof ChangeEntry.FieldChange fc) {
            result.add(new ChangeEntry.GenericChange(labelKey.key(), fc.from(), fc.to()));
        } else {
            result.add(new ChangeEntry.GenericChange(labelKey.key(), null, currentValue));
        }
    }
}
