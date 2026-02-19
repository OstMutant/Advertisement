package org.ost.advertisement.ui.views.components.buttons;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DeleteActionButton extends BaseActionButton {

    @Value
    @lombok.Builder
    public static class Config implements BaseConfig {
        @NonNull String tooltip;
        @NonNull Runnable onClick;
        String cssClassName;
        @lombok.Builder.Default boolean small = false;
    }

    protected DeleteActionButton setupButton(Config config) {
        setIcon(VaadinIcon.TRASH.create());
        addThemeVariants(config.isSmall()
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR});
        super.applyConfig(config);
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<DeleteActionButton> provider;

        public DeleteActionButton build(Config config) {
            return provider.getObject().setupButton(config);
        }
    }
}
