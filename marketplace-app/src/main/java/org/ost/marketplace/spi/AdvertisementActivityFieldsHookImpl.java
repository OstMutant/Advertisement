package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdvertisementActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final I18nService i18n;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.ADVERTISEMENT;
    }

    @Override
    public List<ChangeEntry> expandFields(AuditActivityItemDto item) {
        if (!(item.snapshotData() instanceof AdvertisementSnapshotDto state)) return item.changes();
        List<ChangeEntry> result = new ArrayList<>();
        addField(result, item.changes(), "title",       state.title());
        addField(result, item.changes(), "description", state.description());
        return result;
    }

    @Override
    public String labelFor(String rawFieldKey) {
        return switch (rawFieldKey) {
            case "title"       -> i18n.get(I18nKey.CHANGES_FIELD_TITLE);
            case "description" -> i18n.get(I18nKey.CHANGES_FIELD_DESCRIPTION);
            default            -> rawFieldKey;
        };
    }

    private static void addField(List<ChangeEntry> result, List<ChangeEntry> changes,
                                  String field, String currentValue) {
        ChangeEntry existing = changes.stream()
                .filter(c -> c instanceof ChangeEntry.FieldChange fc && field.equals(fc.field()))
                .findFirst().orElse(null);
        if (existing instanceof ChangeEntry.FieldChange fc) {
            result.add(new ChangeEntry.FieldChange(field, fc.from(), fc.to()));
        } else {
            result.add(new ChangeEntry.FieldChange(field, null, currentValue));
        }
    }
}
