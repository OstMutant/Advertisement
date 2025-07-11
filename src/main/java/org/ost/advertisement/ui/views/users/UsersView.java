package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.components.FloatingActionButton;

@SpringComponent
@UIScope
public class UsersView extends VerticalLayout {

	private final UserListView userListView;
	private final UserRepository userRepository;

	public UsersView(UserListView userListView, UserRepository userRepository) {
		this.userListView = userListView;
		this.userRepository = userRepository;

		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(userListView);
		add(new FloatingActionButton(com.vaadin.flow.component.icon.VaadinIcon.PLUS, "Add User", e ->
			openUserFormDialog(null)
		));
	}

	private void openUserFormDialog(User user) {
		UserFormDialog dialog = new UserFormDialog(user, userRepository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				userListView.refreshAll();
			}
		});
		dialog.open();
	}
}
