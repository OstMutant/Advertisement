package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.Configurable;
import org.springframework.context.annotation.Scope;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSnapshotBinder<T extends AuditableSnapshot>
        implements Configurable<AuditSnapshotBinder<T>, AuditSnapshotBinder.Parameters<T>>, AuditActivityRowHook<T> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull EntityType         entityType;
        @NonNull Predicate<T>       isCurrent;
        Long                        subjectEntityId;
        BiConsumer<Long, Long>      onRestore;
    }

    private final I18nService i18n;
    private Parameters<T> params;

    @Override
    public AuditSnapshotBinder<T> configure(Parameters<T> p) {
        this.params = p;
        return this;
    }

    @Override
    public EntityType entityType() {
        return params.getEntityType();
    }

    @Override
    public Component decorate(@NonNull AuditActivityItemDto<T> item) {
        if (item.snapshotId() == null || item.snapshotData() == null) return null;
        T snap = item.snapshotData();
        if (params.getIsCurrent().test(snap)) {
            Span badge = new Span(i18n.get(AuditI18n.ACTIVITY_CURRENT_STATE));
            badge.addClassName("activity-feed-current-badge");
            return badge;
        }
        if (params.getOnRestore() == null) return null;
        if (params.getSubjectEntityId() != null && !Objects.equals(params.getSubjectEntityId(), item.entityId())) return null;
        Button btn = new Button(i18n.get(AuditI18n.ACTIVITY_RESTORE));
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClassName("entity-activity-restore-btn");
        btn.addClickListener(_ -> params.getOnRestore().accept(item.snapshotId(), item.entityId()));
        return btn;
    }
}
