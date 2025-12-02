package org.ost.advertisement.ui.views.components;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constants.I18nKey.ACTIONS_CLEAR_TOOLTIP;
import static org.ost.advertisement.ui.views.components.ContentFactory.createSvgButton;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.services.I18nService;

public class ActionBlock implements ActionStateChangeListener {

	private final Button applyButton;
	private final Button clearButton;

	public ActionBlock(I18nService i18n) {
		applyButton = createSvgButton("apply.svg", i18n.get(ACTIONS_APPLY_TOOLTIP), ButtonVariant.LUMO_PRIMARY);
		clearButton = createSvgButton("clear.svg", i18n.get(ACTIONS_CLEAR_TOOLTIP), ButtonVariant.LUMO_TERTIARY);
	}

	public void eventProcessor(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void setChanged(boolean changed) {
		if (changed) {
			applyButton.getStyle().set("border-color", "orange");
		} else {
			applyButton.getStyle().set("border-color", "transparent");
		}
	}

	public Component getComponent() {
		HorizontalLayout layout = new HorizontalLayout(applyButton, clearButton);
		layout.setSpacing(false);
		layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return layout;
	}
}

