package org.ost.advertisement.ui.views.components.sort;

import static org.ost.advertisement.constans.I18nKey.SORT_ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constans.I18nKey.SORT_ACTIONS_CLEAR_TOOLTIP;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.NoArgsConstructor;
import org.ost.advertisement.services.I18nService;

@NoArgsConstructor
public class SortActionsBlock implements SortFieldsProcessorEvents {

	private I18nService i18n;

	protected Button applyButton;
	protected Button clearButton;

	public SortActionsBlock(I18nService i18n) {
		this.i18n = i18n;
		this.applyButton = createButton("apply.svg", i18n.get(SORT_ACTIONS_APPLY_TOOLTIP),
			ButtonVariant.LUMO_PRIMARY);
		this.clearButton = createButton("clear.svg", i18n.get(SORT_ACTIONS_CLEAR_TOOLTIP),
			ButtonVariant.LUMO_TERTIARY);
	}

	public void eventProcessor(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void onEventSortChanged(boolean isActive) {
		applyButton.getStyle().remove("border");
		applyButton.getStyle().remove("border-radius");
		if (isActive) {
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

	private Button createButton(String svgPath, String tooltip, ButtonVariant variant) {
		Button button = new Button(new SvgSortIcon("icons/" + svgPath));
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}

}
