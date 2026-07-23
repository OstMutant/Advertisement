package org.ost.marketplace.ui.query.elements.action;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import org.ost.marketplace.ui.query.elements.SvgIcon;
import org.ost.marketplace.ui.query.utils.HighlighterUtil;

public class QueryActionButton extends Button {

    public QueryActionButton(String svgPath, String tooltip, ButtonVariant variant) {
        setText("");
        HighlighterUtil.setDefaultBorder(this);
        setIcon(new SvgIcon("icons/" + svgPath));
        getElement().setProperty("title", tooltip);
        getElement().setAttribute("aria-label", tooltip);
        addThemeVariants(variant, ButtonVariant.LUMO_ICON);
    }
}
