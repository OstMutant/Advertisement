package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.platform.ui.Configurable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.Objects;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSnapshotBinder<T>
        implements Configurable<AuditSnapshotBinder<T>, AuditSnapshotBinder.Parameters<T>>, AuditActivityRowHook {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull EntityType         entityType;
        @NonNull Class<T>           snapshotClass;
        @NonNull Predicate<T>       isCurrent;
        Long                        subjectEntityId;
        BiConsumer<Long, Long>      onRestore;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder<T> extends ComponentBuilder<AuditSnapshotBinder<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<AuditSnapshotBinder<T>> provider;
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
    public Component decorate(AuditActivityItemDto item) {
        if (item.snapshotId() == null || item.snapshotId() <= 0) return null;
        AuditableSnapshot raw = item.snapshotData();
        return Optional.ofNullable(raw)
                .filter(params.getSnapshotClass()::isInstance)
                .map(params.getSnapshotClass()::cast)
                .map(snap -> {
                    if (params.getIsCurrent().test(snap)) {
                        Span badge = new Span(i18n.get(AuditI18n.ACTIVITY_CURRENT_STATE));
                        badge.addClassName("activity-feed-current-badge");
                        return (Component) badge;
                    }
                    if (params.getOnRestore() == null) return null;
                    if (params.getSubjectEntityId() != null && !Objects.equals(params.getSubjectEntityId(), item.entityId())) return null;
                    Button btn = new Button(i18n.get(AuditI18n.ACTIVITY_RESTORE));
                    btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    btn.addClassName("entity-history-restore-btn");
                    btn.addClickListener(_ -> params.getOnRestore().accept(item.snapshotId(), item.entityId()));
                    return (Component) btn;
                })
                .orElse(null);
    }
}
