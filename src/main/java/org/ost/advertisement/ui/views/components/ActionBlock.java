package org.ost.advertisement.ui.views.components;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constants.I18nKey.ACTIONS_CLEAR_TOOLTIP;

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
		this.applyButton = ContentFactory.createSvgButton("apply.svg", i18n.get(ACTIONS_APPLY_TOOLTIP),
			ButtonVariant.LUMO_PRIMARY);
		this.clearButton = ContentFactory.createSvgButton("clear.svg", i18n.get(ACTIONS_CLEAR_TOOLTIP),
			ButtonVariant.LUMO_TERTIARY);
	}

	public void eventProcessor(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void setChanged(boolean changed) {
		applyButton.getStyle().remove("border");
		applyButton.getStyle().remove("border-radius");
		if (changed) {
			applyButton.getStyle().set("border", "3px solid orange");
			applyButton.getStyle().set("border-radius", "4px");
		}
	}

	public Component getComponent() {
		HorizontalLayout layout = new HorizontalLayout(applyButton, clearButton);
		layout.setSpacing(false);
		layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return layout;
	}
}

