package org.ost.advertisement.ui.views.components.query.elements.action.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.HighlighterUtil;
import org.ost.advertisement.ui.views.components.SvgIcon;

public class QueryActionButton extends Button {

    @Value
    @Builder
    public static class Parameters {

        @NonNull
        I18nService i18n;
        @NonNull
        String svgPath;
        @NonNull
        I18nKey tooltipKey;
        @NonNull
        ButtonVariant variant;
    }

    protected QueryActionButton(@NonNull Parameters parameters) {
        setText("");
        HighlighterUtil.setDefaultBorder(this);
        setIcon(new SvgIcon("icons/" + parameters.getSvgPath()));
        getElement().setProperty("title", parameters.i18n.get(parameters.getTooltipKey()));
        addThemeVariants(parameters.getVariant(), ButtonVariant.LUMO_ICON);
    }

}
