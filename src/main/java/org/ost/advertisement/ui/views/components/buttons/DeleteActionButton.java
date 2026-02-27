package org.ost.advertisement.ui.views.components.buttons;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.builder.ComponentBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DeleteActionButton extends BaseActionButton
        implements Configurable<DeleteActionButton, DeleteActionButton.Parameters> {

    @Value
    @lombok.Builder
    public static class Parameters implements BaseConfig {
        @NonNull String   tooltip;
        @NonNull Runnable onClick;
        String            cssClassName;
        @lombok.Builder.Default boolean small = false;
    }

    @Override
    public DeleteActionButton configure(Parameters p) {
        setIcon(VaadinIcon.TRASH.create());
        addThemeVariants(p.isSmall()
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR});
        applyConfig(p);
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<DeleteActionButton, Parameters> {
        @Getter
        private final ObjectProvider<DeleteActionButton> provider;
    }
}