package org.ost.audit.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.spi.ActivityRowBinding;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.ui.ComponentBuilder;
import org.ost.platform.core.ui.Configurable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

@SpringComponent
@ConditionalOnAuditEnabled
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class SnapshotBinder<T>
        implements Configurable<SnapshotBinder<T>, SnapshotBinder.Parameters<T>>, ActivityRowBinding {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull EntityType         entityType;
        @NonNull Class<T>           snapshotClass;
        @NonNull Predicate<T>       isCurrent;
        BiConsumer<Long, T>         onRestore;  // null → no restore button
        @NonNull String             currentLabel;
        String                      restoreLabel;
    }

    @SpringComponent
    @ConditionalOnAuditEnabled
    @Scope("prototype")
    @RequiredArgsConstructor
    public static class Builder<T> extends ComponentBuilder<SnapshotBinder<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<SnapshotBinder<T>> provider;
    }

    @Qualifier("auditObjectMapper")
    private final ObjectMapper objectMapper;

    private Parameters<T> params;

    @Override
    public SnapshotBinder<T> configure(Parameters<T> p) {
        this.params = p;
        return this;
    }

    @Override
    public EntityType entityType() {
        return params.getEntityType();
    }

    @Override
    public Component decorate(ActivityItemDto item) {
        if (item.snapshotId() == null || item.snapshotId() <= 0) return null;
        if (item.snapshotData() == null || item.snapshotData().isEmpty()) return null;
        T snap;
        try {
            snap = objectMapper.readValue(item.snapshotData().json(), params.getSnapshotClass());
        } catch (Exception _) {
            return null;
        }
        if (params.getIsCurrent().test(snap)) {
            Span badge = new Span(params.getCurrentLabel());
            badge.addClassName("activity-feed-current-badge");
            return badge;
        }
        if (params.getOnRestore() == null) return null;
        Button btn = new Button(params.getRestoreLabel());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClassName("entity-history-restore-btn");
        btn.addClickListener(_ -> params.getOnRestore().accept(item.snapshotId(), snap));
        return btn;
    }
}
