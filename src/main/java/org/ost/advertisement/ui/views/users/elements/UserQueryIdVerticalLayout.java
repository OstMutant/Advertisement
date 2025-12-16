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
@SuppressWarnings("java:S2094")
public class UserQueryIdVerticalLayout extends QueryVerticalLayout {

	private final UserQueryIdMinField idMin;
	private final UserQueryIdMaxField idMax;

	@PostConstruct
	protected void initLayout() {
		super.initLayout(idMin, idMax);
	}
}
