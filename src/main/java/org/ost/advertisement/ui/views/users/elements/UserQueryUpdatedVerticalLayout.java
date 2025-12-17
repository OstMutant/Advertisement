package org.ost.advertisement.ui.views.users.elements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.ui.views.components.query.elements.cell.QueryVerticalLayout;


@SpringComponent
@UIScope
@AllArgsConstructor
@Getter
public class UserQueryUpdatedVerticalLayout extends QueryVerticalLayout {

	private final UserQueryUpdatedStartDatePickerField updatedStart;
	private final UserQueryUpdatedEndDatePickerField updatedEnd;

	@PostConstruct
	protected void initLayout() {
		super.initLayout(updatedStart, updatedEnd);
	}
}
