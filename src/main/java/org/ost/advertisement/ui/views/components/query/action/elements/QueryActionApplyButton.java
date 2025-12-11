package org.ost.advertisement.ui.views.components.query.action.elements;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_APPLY_TOOLTIP;

import com.vaadin.flow.component.button.ButtonVariant;
import org.ost.advertisement.services.I18nService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class QueryActionApplyButton extends QueryActionButton {

	public QueryActionApplyButton(I18nService i18n) {
		super("apply.svg", i18n.get(ACTIONS_APPLY_TOOLTIP), ButtonVariant.LUMO_PRIMARY);
	}

}
