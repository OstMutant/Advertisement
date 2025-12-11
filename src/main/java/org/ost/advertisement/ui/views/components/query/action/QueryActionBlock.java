package org.ost.advertisement.ui.views.components.query.action;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.AllArgsConstructor;
import org.ost.advertisement.ui.views.components.query.action.elements.QueryActionApplyButton;
import org.ost.advertisement.ui.views.components.query.action.elements.QueryActionClearButton;
import org.springframework.context.annotation.Scope;

@org.springframework.stereotype.Component
@Scope("prototype")
@AllArgsConstructor
public class QueryActionBlock implements QueryActionBlockHandler {

	private final QueryActionApplyButton applyButton;
	private final QueryActionClearButton clearButton;

	public void addEventListener(Runnable onApply, Runnable onClear) {
		applyButton.addClickListener(e -> onApply.run());
		clearButton.addClickListener(e -> onClear.run());
	}

	@Override
	public void updateDirtyState(boolean dirty) {
		applyButton.setDirty(dirty);
	}

	public Component getComponent() {
		HorizontalLayout layout = new HorizontalLayout(applyButton, clearButton);
		layout.setSpacing(false);
		layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return layout;
	}
}

