package org.ost.advertisement.ui.views.components.dialogs;

import static org.ost.advertisement.ui.views.components.dialogs.DialogStyle.wrapScrollable;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class DialogLayout {

	private final VerticalLayout root = new VerticalLayout();
	private final FormLayout form = new FormLayout();
	private final HorizontalLayout actions = new HorizontalLayout();

	public DialogLayout() {
		DialogStyle.applyFormLayout(form);
		DialogStyle.applyActionsLayout(actions);
		DialogStyle.applyRootLayout(root);

		root.add(wrapScrollable(form), actions);
	}

	public void setHeader(String titleText) {
		H2 title = new H2(titleText);
		title.addClassName("dialog-title");
		root.addComponentAsFirst(title);
	}

	public void addFormContent(Component... components) {
		form.add(components);
	}

	public void addActions(Component... buttons) {
		actions.add(buttons);
	}

	public Component getLayout() {
		return root;
	}
}


