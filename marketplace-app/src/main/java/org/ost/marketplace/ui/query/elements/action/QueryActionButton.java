package org.ost.marketplace.ui.query.elements.action;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import jakarta.annotation.PostConstruct;
import lombok.*;

import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.query.elements.SvgIcon;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;

import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.query.utils.HighlighterUtil;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryActionButton extends Button implements Configurable<QueryActionButton, QueryActionButton.Parameters>, Initialization<QueryActionButton> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String svgPath;
        @NonNull I18nKey tooltipKey;
        @NonNull ButtonVariant variant;
    }

    @Getter
    private final transient I18nService               i18nService;
    private final transient UiComponentFactory<SvgIcon>  svgIconFactory;

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
        getElement().setProperty("title", i18nService.get(p.getTooltipKey()));
        addThemeVariants(p.getVariant(), ButtonVariant.LUMO_ICON);
        return this;
    }

    private SvgIcon createSvgIcon(String svgPath) {
        return svgIconFactory.build(SvgIcon.Parameters.builder().resourcePath("icons/" + svgPath).build());
    }
}
