package org.ost.marketplace.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.Button;

public class BaseActionButton extends Button {

    protected void applyConfig(String tooltip, Runnable onClick, String cssClassName) {
        if (cssClassName != null) {
            addClassName(cssClassName);
        }
        getElement().setAttribute("title", tooltip);
        getElement().setAttribute("aria-label", tooltip);
        addClickListener(_ -> onClick.run());
        getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()");
    }
}
