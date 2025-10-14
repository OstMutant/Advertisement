package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class DialogLayout {

	private final VerticalLayout root = new VerticalLayout();
	private final FormLayout form = new FormLayout();
	private final HorizontalLayout actions = new HorizontalLayout();

	public DialogLayout() {
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
		form.setWidthFull();

		actions.setSpacing(true);
		actions.setJustifyContentMode(JustifyContentMode.END);
		actions.setWidthFull();

		root.setPadding(true);
		root.setSpacing(false);
		root.setHeight("100%");
		root.add(form, actions);
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

