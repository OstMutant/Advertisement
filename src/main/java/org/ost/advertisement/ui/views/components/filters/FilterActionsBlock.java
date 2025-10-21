package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.constans.I18nKey.FILTER_ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constans.I18nKey.FILTER_ACTIONS_CLEAR_TOOLTIP;
import static org.ost.advertisement.ui.views.components.ContentFactory.createSvgButton;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.ActionStateChangeListener;

public class FilterActionsBlock implements ActionStateChangeListener {

	private Button applyButton;
	private Button clearButton;

	public FilterActionsBlock(I18nService i18n) {
		this.applyButton = createSvgButton("apply.svg", i18n.get(FILTER_ACTIONS_APPLY_TOOLTIP),
			ButtonVariant.LUMO_PRIMARY);
		this.clearButton = createSvgButton("clear.svg", i18n.get(FILTER_ACTIONS_CLEAR_TOOLTIP),
			ButtonVariant.LUMO_TERTIARY);
	}

	public void eventProcessor(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void onActionStateChanged(boolean isChanged) {
		applyButton.getStyle().remove("border");
		applyButton.getStyle().remove("border-radius");
		if (isChanged) {
			applyButton.getStyle().set("border", "3px solid orange");
			applyButton.getStyle().set("border-radius", "4px");
		}
	}

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
