package org.ost.advertisement.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DeleteActionButton extends BaseActionButton
        implements Configurable<DeleteActionButton, DeleteActionButton.Parameters>, Initialization<DeleteActionButton> {

    @Value
    @lombok.Builder
    public static class Parameters implements BaseConfig {
        @NonNull String   tooltip;
        @NonNull Runnable onClick;
        String            cssClassName;
        @lombok.Builder.Default boolean small = false;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<DeleteActionButton, Parameters> {
        @Getter
        private final ObjectProvider<DeleteActionButton> provider;
    }

    @Override
    @PostConstruct
    public DeleteActionButton init() {
        setIcon(VaadinIcon.TRASH.create());
        return this;
    }

    @Override
    public DeleteActionButton configure(Parameters p) {
        addThemeVariants(p.isSmall()
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR});
        applyConfig(p);
        return this;
    }
}
