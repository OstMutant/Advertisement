package org.ost.advertisement.ui.views.components.query.action.elements;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_CLEAR_TOOLTIP;

import com.vaadin.flow.component.button.ButtonVariant;
import org.ost.advertisement.services.I18nService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class QueryActionClearButton extends QueryActionButton {

	public QueryActionClearButton(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.svgPath("clear.svg")
			.tooltipKey(ACTIONS_CLEAR_TOOLTIP)
			.variant(ButtonVariant.LUMO_TERTIARY)
			.build());
	}
}
