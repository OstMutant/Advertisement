package org.ost.advertisement.ui.views.components.query.action.elements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.*;
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
        getStyle().set("border", "3px solid " + HighlighterUtil.DirtyHighlightColor.CLEAN.getCssColor());
        getStyle().set("border-radius", "4px");
        setIcon(new SvgIcon("icons/" + parameters.getSvgPath()));
        getElement().setProperty("title", parameters.i18n.get(parameters.getTooltipKey()));
        addThemeVariants(parameters.getVariant(), ButtonVariant.LUMO_ICON);
    }

}
