package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.repository.UserRepository;

public class UsersView extends VerticalLayout {

	public UsersView(UserRepository userRepository) {
		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(new UserListView(userRepository));
	}
}
