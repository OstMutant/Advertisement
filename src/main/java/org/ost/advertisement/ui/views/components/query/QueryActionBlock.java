package org.ost.advertisement.ui.views.components.query;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constants.I18nKey.ACTIONS_CLEAR_TOOLTIP;
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createSvgActionButton;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.springframework.context.annotation.Scope;

@org.springframework.stereotype.Component
@Scope("prototype")
public class QueryActionBlock implements QueryActionBlockHandler {

	private final Button applyButton;
	private final Button clearButton;

	public QueryActionBlock(I18nService i18n) {
		applyButton = createSvgActionButton("apply.svg", i18n.get(ACTIONS_APPLY_TOOLTIP), ButtonVariant.LUMO_PRIMARY);
		clearButton = createSvgActionButton("clear.svg", i18n.get(ACTIONS_CLEAR_TOOLTIP), ButtonVariant.LUMO_TERTIARY);
	}

	public void addEventListener(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void updateDirtyState(boolean dirty) {
		applyButton.getStyle().set("border-color",
			dirty ? DirtyHighlightColor.DIRTY.getCssColor() : DirtyHighlightColor.CLEAN.getCssColor());
	}

	public Component getComponent() {
		HorizontalLayout layout = new HorizontalLayout(applyButton, clearButton);
		layout.setSpacing(false);
		layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return layout;
	}

	@AllArgsConstructor
	@Getter
	public enum DirtyHighlightColor {
		CLEAN("transparent"),
		DIRTY("orange");

		private final String cssColor;
	}
}

