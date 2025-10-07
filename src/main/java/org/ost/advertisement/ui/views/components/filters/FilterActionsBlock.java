package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.constans.I18nKey.FILTER_ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constans.I18nKey.FILTER_ACTIONS_CLEAR_TOOLTIP;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.NoArgsConstructor;
import org.ost.advertisement.services.I18nService;

@NoArgsConstructor
public class FilterActionsBlock implements FilterFieldsProcessorEvents {

	private I18nService i18n;

	private Button applyButton;
	private Button clearButton;

	public FilterActionsBlock(I18nService i18n) {
		this.i18n = i18n;
		this.applyButton = createButton(VaadinIcon.FILTER, i18n.get(FILTER_ACTIONS_APPLY_TOOLTIP),
			ButtonVariant.LUMO_PRIMARY);
		this.clearButton = createButton(VaadinIcon.ERASER, i18n.get(FILTER_ACTIONS_CLEAR_TOOLTIP),
			ButtonVariant.LUMO_TERTIARY);
	}

	public void eventProcessor(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void onEventFilterChanged(boolean isFilterChanged) {
		applyButton.getStyle().remove("border");
		applyButton.getStyle().remove("border-radius");
		if (isFilterChanged) {
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

	private Button createButton(VaadinIcon icon, String tooltip, ButtonVariant variant) {
		Button button = new Button(icon.create());
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}
}

