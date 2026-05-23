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
import org.ost.platform.audit.codec.SnapshotCodec;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.spi.ActivityRowHook;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.platform.ui.Configurable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class SnapshotBinder<T>
        implements Configurable<SnapshotBinder<T>, SnapshotBinder.Parameters<T>>, ActivityRowHook {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull EntityType         entityType;
        @NonNull Class<T>           snapshotClass;
        @NonNull Predicate<T>       isCurrent;
        BiConsumer<Long, T>         onRestore;
        @NonNull String             currentLabel;
        String                      restoreLabel;
    }

    @SpringComponent
    @Scope("prototype")
    @RequiredArgsConstructor
    public static class Builder<T> extends ComponentBuilder<SnapshotBinder<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<SnapshotBinder<T>> provider;
    }

    private final SnapshotCodec snapshotCodec;

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
        return snapshotCodec.decode(item.snapshotData(), params.getSnapshotClass())
                .map(snap -> {
                    if (params.getIsCurrent().test(snap)) {
                        Span badge = new Span(params.getCurrentLabel());
                        badge.addClassName("activity-feed-current-badge");
                        return (Component) badge;
                    }
                    if (params.getOnRestore() == null) return null;
                    Button btn = new Button(params.getRestoreLabel());
                    btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    btn.addClassName("entity-history-restore-btn");
                    btn.addClickListener(_ -> params.getOnRestore().accept(item.snapshotId(), snap));
                    return (Component) btn;
                })
                .orElse(null);
    }
}
