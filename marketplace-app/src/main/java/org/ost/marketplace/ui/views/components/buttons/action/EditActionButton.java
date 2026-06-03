package org.ost.marketplace.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EditActionButton extends BaseActionButton
        implements Configurable<EditActionButton, EditActionButton.Parameters>, Initialization<EditActionButton> {

    @Value
    @lombok.Builder
    public static class Parameters implements BaseConfig {
        @NonNull String   tooltip;
        @NonNull Runnable onClick;
        String            cssClassName;
        @lombok.Builder.Default boolean small = false;
    }

    @Override
    @PostConstruct
    public EditActionButton init() {
        setIcon(VaadinIcon.EDIT.create());
        return this;
    }

    @Override
    public EditActionButton configure(Parameters p) {
        addThemeVariants(p.isSmall()
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE});
        applyConfig(p);
        return this;
    }
}
