package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.TailwindStyle;

public abstract class DialogForm extends Dialog {

	private final H2 title = new H2();
	private final FormLayout content = new FormLayout();
	private final HorizontalLayout actionsFooter = new HorizontalLayout();
	protected final transient I18nService i18n;

	protected DialogForm(I18nService i18n) {
		this.i18n = i18n;
		initLayout();
	}

	private void initLayout() {
		setModal(true);
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);

		addClassName("dialog-form");

		content.setWidthFull();
		content.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
		actionsFooter.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		actionsFooter.setWidthFull();

		VerticalLayout layout = new VerticalLayout(title, createScrollable(content), actionsFooter);
		layout.setPadding(true);
		layout.setSpacing(false);
		layout.setHeight("100%");

		add(layout);
	}

	protected void addContent(Component... components) {
		content.add(components);
	}

	protected void addActions(Component... components) {
		actionsFooter.add(components);
	}

	protected void setTitle(I18nKey key) {
		title.setText(i18n.get(key));
	}

	private Div createScrollable(Component inner) {
		Div scroll = new Div(inner);
		TailwindStyle.SCROLL_CONTAINER.apply(scroll);
		return scroll;
	}
}


