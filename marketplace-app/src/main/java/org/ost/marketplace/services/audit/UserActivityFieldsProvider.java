package org.ost.marketplace.services.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.ActivityItemFieldsProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserActivityFieldsProvider implements ActivityItemFieldsProvider {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.USER;
    }

    @Override
    public List<ChangeEntry> expandFields(ActivityItemDto item) {
        try {
            UserSnapshot state = objectMapper.readValue(item.snapshotData().json(), UserSnapshot.class);
            List<ChangeEntry> result = new ArrayList<>();
            addField(result, item.changes(), "name",  state.name());
            addField(result, item.changes(), "email", state.email());
            addField(result, item.changes(), "role",  state.role());
            return result;
        } catch (Exception _) {
            return item.changes();
        }
    }

    private static void addField(List<ChangeEntry> result, List<ChangeEntry> changes, String field, String currentValue) {
        changes.stream()
                .filter(c -> c instanceof ChangeEntry.FieldChange fc && field.equals(fc.field()))
                .findFirst()
                .ifPresentOrElse(
                        result::add,
                        () -> result.add(new ChangeEntry.FieldChange(field, null, currentValue))
                );
    }
}
