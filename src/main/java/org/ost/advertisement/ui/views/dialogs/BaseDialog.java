package org.ost.advertisement.ui.views.dialogs;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;

public class BaseDialog extends Dialog {

	protected final H2 title;
	protected final FormLayout content;
	protected final HorizontalLayout actionsFooter;

	protected static void updateElementBy13pxGray(Element element) {
		element.getStyle()
			.set("font-size", "13px")
			.set("color", "gray")
			.set("padding", "4px 8px");
	}

	protected static void updateElementByEmailStyle(Element element) {
		element.getStyle()
			.set("font-size", "14px")
			.set("font-weight", "bold")
			.set("color", "var(--lumo-primary-text)")
			.set("padding", "4px 8px");
	}

	public BaseDialog() {
		setModal(true);
		setDraggable(false);
		setResizable(false);
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);

		title = new H2();
		content = createFormLayout();
		actionsFooter = createButtonBar();

		VerticalLayout layout = new VerticalLayout();
		layout.setPadding(true);
		layout.setSpacing(false);
		layout.setHeight("100%");

		layout.add(title, createScrollableComponent(content), actionsFooter);

		add(layout);
	}

	private Div createScrollableComponent(Component content) {
		Div scrollArea = new Div(content);
		scrollArea.setHeight("100%");
		scrollArea.getStyle()
			.set("overflow", "auto")
			.set("flex-grow", "1");

		return scrollArea;
	}

	private FormLayout createFormLayout() {
		FormLayout layout = new FormLayout();
		layout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
		return layout;
	}

	private HorizontalLayout createButtonBar() {
		HorizontalLayout buttonBar = new HorizontalLayout();
		buttonBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		buttonBar.setWidthFull();
		return buttonBar;
	}

	protected Span createEmailSpan() {
		Span span = new Span();
		updateElementByEmailStyle(span.getElement());
		return span;
	}

	protected HorizontalLayout createEmailComponent(String labelText, Span span) {
		Span label = new Span(labelText);
		updateElementByEmailStyle(label.getElement());
		return new HorizontalLayout(label, span);
	}

	protected Span createDateSpan() {
		Span span = new Span();
		updateElementBy13pxGray(span.getElement());
		return span;
	}

	protected HorizontalLayout createDateComponent(String labelText, Span span) {
		Span label = new Span(labelText);
		updateElementBy13pxGray(label.getElement());
		return new HorizontalLayout(label, span);
	}

	protected Button createSaveButton(ComponentEventListener<ClickEvent<Button>> clickListener) {
		Button saveButton = new Button("Save", clickListener);
		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return saveButton;
	}

	protected Button createCancelButton() {
		Button cancelButton = new Button("Cancel", e -> close());
		cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		return cancelButton;
	}
}
