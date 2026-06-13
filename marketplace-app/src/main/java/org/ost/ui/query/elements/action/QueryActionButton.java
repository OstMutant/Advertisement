package org.ost.ui.query.elements.action;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.i18n.TranslationKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.ui.query.elements.SvgIcon;
import org.ost.platform.ui.Configurable;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.ost.ui.query.utils.HighlighterUtil;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryActionButton extends Button implements Configurable<QueryActionButton, QueryActionButton.Parameters>, Translatable, Initialization<QueryActionButton> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String svgPath;
        @NonNull TranslationKey tooltipKey;
        @NonNull ButtonVariant variant;
    }

    @Getter
    private final transient I18nService               i18nService;
    private final transient ComponentFactory<SvgIcon>  svgIconFactory;

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
        return svgIconFactory.build(SvgIcon.Parameters.builder().resourcePath("icons/" + svgPath).build());
    }
}
