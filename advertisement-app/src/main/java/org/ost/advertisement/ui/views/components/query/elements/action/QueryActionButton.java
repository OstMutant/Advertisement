package org.ost.advertisement.ui.views.components.query.elements.action;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SvgIcon;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.ost.advertisement.ui.views.utils.HighlighterUtil;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryActionButton extends Button implements Configurable<QueryActionButton, QueryActionButton.Parameters>, I18nParams, Initialization<QueryActionButton> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String svgPath;
        @NonNull I18nKey tooltipKey;
        @NonNull ButtonVariant variant;
    }

    @Getter
    private final transient I18nService i18nService;
    private final transient SvgIcon.Builder svgIconBuilder;

    @Override
    @PostConstruct
    public QueryActionButton init() {
        setText("");
        HighlighterUtil.setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryActionButton configure(Parameters p) {
        setIcon(createSvgIcon(p.getSvgPath()));
        getElement().setProperty("title", getValue(p.getTooltipKey()));
        addThemeVariants(p.getVariant(), ButtonVariant.LUMO_ICON);
        return this;
    }

    private SvgIcon createSvgIcon(String svgPath) {
        return svgIconBuilder.build(SvgIcon.Parameters.builder().resourcePath("icons/" + svgPath).build());
    }
}
