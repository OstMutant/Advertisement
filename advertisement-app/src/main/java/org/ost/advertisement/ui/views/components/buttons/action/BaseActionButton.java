package org.ost.advertisement.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.Button;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseActionButton extends Button {

    public interface BaseConfig {
        String getTooltip();
        Runnable getOnClick();
        String getCssClassName();
        boolean isSmall();
    }

    protected void applyConfig(BaseConfig config) {
        if (config.getCssClassName() != null) {
            addClassName(config.getCssClassName());
        }
        getElement().setAttribute("title", config.getTooltip());
        addClickListener(_ -> config.getOnClick().run());
        getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()");
    }
}
