package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@SpringComponent
@UIScope
public class UsersView extends VerticalLayout {

	public UsersView() {
		add(new H2("Вкладка Юзери"),
			new Paragraph("Тут буде вміст, пов'язаний з юзерами."));
		setSizeFull();
	}
}
