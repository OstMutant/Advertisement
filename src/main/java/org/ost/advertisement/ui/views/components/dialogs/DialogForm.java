package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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


	protected Button createSaveButton(String i18nKey, ComponentEventListener<ClickEvent<Button>> listener) {
		return createButton(i18nKey, listener, "primary");
	}

	protected Button createCancelButton(String i18nKey) {
		return createButton(i18nKey, e -> close(), "tertiary");
	}

	protected Button createButton(String i18nKey, ComponentEventListener<ClickEvent<Button>> listener,
								  String... themeNames) {
		Button button = new Button(i18n.get(i18nKey), listener);
		button.addThemeNames(themeNames);
		return button;
	}

	protected HorizontalLayout labeled(String i18nKey, Component value, TailwindStyle... styles) {
		Span label = new Span(i18n.get(i18nKey));
		TailwindStyle.applyAll(label, styles);
		TailwindStyle.applyAll(value, styles);
		return new HorizontalLayout(label, value);
	}

	protected HorizontalLayout labeled(String i18nKey, String text, TailwindStyle... styles) {
		return labeled(i18nKey, new Span(text), styles);
	}

	protected void addContent(Component... components) {
		content.add(components);
	}

	protected void addActions(Component... components) {
		actionsFooter.add(components);
	}

	protected void setTitle(String i18nKey) {
		title.setText(i18n.get(i18nKey));
	}

	private Div createScrollable(Component inner) {
		Div scroll = new Div(inner);
		TailwindStyle.SCROLL_CONTAINER.apply(scroll);
		return scroll;
	}
}

