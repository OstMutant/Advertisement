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
		setPadding(false);
		setSpacing(false);

		addUserButton = new Button(VaadinIcon.PLUS.create());
		addUserButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ICON);
		addUserButton.setText("");
		addUserButton.getElement().setProperty("title", "Add User");

		addUserButton.getStyle()
			.set("position", "fixed")
			.set("bottom", "32px")
			.set("right", "32px")
			.set("z-index", "1000")
			.set("border-radius", "50%")
			.set("width", "48px")
			.set("height", "48px")
			.set("box-shadow", "0 2px 6px rgba(0,0,0,0.3)");

		addUserButton.addClickListener(event -> openUserFormDialog(null));

		HorizontalLayout toolbar = new HorizontalLayout();
		toolbar.setWidthFull();

		add(toolbar, userListView);
		add(addUserButton);
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
