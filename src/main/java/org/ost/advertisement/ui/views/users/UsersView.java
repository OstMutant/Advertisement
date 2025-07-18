package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.repository.UserRepository;

public class UsersView extends VerticalLayout {

	private final UserListView userListView;

	public UsersView(UserRepository userRepository) {
		this.userListView = new UserListView(userRepository);

		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(userListView);
	}
}
