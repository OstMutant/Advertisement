package org.ost.advertisement.ui.views.components.query.elements.inline;

import com.vaadin.flow.component.textfield.TextField;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

@Getter
public class QueryTextInlineRow extends QueryInlineRow {

	@Value
	@Builder
	public static class Parameters {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey labelI18nKey;
		@NonNull
		SortIcon sortIcon;
		@NonNull
		TextField filterField;
	}

	private final SortIcon sortIcon;
	private final TextField filterField;

	public QueryTextInlineRow(@NonNull Parameters parameters) {
		super(parameters.getI18n(), parameters.getLabelI18nKey());
		sortIcon = parameters.getSortIcon();
		filterField = parameters.getFilterField();
	}

	@PostConstruct
	private void initLayout() {
		initLayout(sortIcon, filterField);
	}
}
