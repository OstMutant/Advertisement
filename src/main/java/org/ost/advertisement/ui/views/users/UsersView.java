package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;

@SpringComponent
@UIScope
public class UsersView extends VerticalLayout {

	private final UserListView userListView;
	private final UserRepository userRepository;
	private Button addUserButton;

	public UsersView(UserListView userListView, UserRepository userRepository) {
		this.userListView = userListView;
		this.userRepository = userRepository;
		setSizeFull();

		addUserButton = new Button("Add User", VaadinIcon.PLUS.create());
		addUserButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		addUserButton.addClickListener(event -> openUserFormDialog(null));

		HorizontalLayout toolbar = new HorizontalLayout(addUserButton);
		toolbar.setWidthFull();
		toolbar.setJustifyContentMode(JustifyContentMode.END);

		add(toolbar, userListView);
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
