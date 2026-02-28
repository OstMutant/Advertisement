package org.ost.advertisement.ui.views.components.query.elements.action;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.HighlighterUtil;
import org.ost.advertisement.ui.utils.I18nParams;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.views.components.SvgIcon;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryActionButton extends Button
        implements Configurable<QueryActionButton, QueryActionButton.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String        svgPath;
        @NonNull I18nKey       tooltipKey;
        @NonNull ButtonVariant variant;
    }

    // -------------------------------------------------------------------------

    @Override
    public QueryActionButton configure(Parameters p) {
        setText("");
        HighlighterUtil.setDefaultBorder(this);
        setIcon(new SvgIcon("icons/" + p.getSvgPath()));
        getElement().setProperty("title", getValue(p.getTooltipKey()));
        addThemeVariants(p.getVariant(), ButtonVariant.LUMO_ICON);
        return this;
    }
}