package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;

public class AdvertisementCardView extends VerticalLayout {

	public AdvertisementCardView(Advertisement ad, AdvertisementRepository repository, Runnable onRefresh) {
		addClassName("advertisement-card");
		getStyle().set("border", "1px solid #ccc");
		getStyle().set("border-radius", "8px");
		getStyle().set("padding", "16px");
		getStyle().set("box-shadow", "2px 2px 6px rgba(0,0,0,0.05)");

		H3 title = new H3(ad.getTitle());
		title.getElement().setProperty("title", ad.getTitle());

		Span description = new Span(ad.getDescription());
		description.getStyle()
			.set("display", "-webkit-box")
			.set("overflow", "hidden")
			.set("text-overflow", "ellipsis")
			.set("word-break", "break-word")
			.set("-webkit-line-clamp", "3")
			.set("-webkit-box-orient", "vertical")
			.set("white-space", "normal");

		Span createdAt = new Span("Created: " + formatInstant(ad.getCreatedAt()));
		Span updatedAt = new Span("Updated: " + formatInstant(ad.getUpdatedAt()));
		Span userId = new Span("User ID: " + ad.getUserId());

		VerticalLayout meta = new VerticalLayout(createdAt, updatedAt, userId);
		meta.setSpacing(false);
		meta.setPadding(false);

		Button edit = new Button("Edit", VaadinIcon.EDIT.create());
		edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		edit.addClickListener(e -> {
			AdvertisementFormDialog dialog = new AdvertisementFormDialog(ad, repository);
			dialog.addOpenedChangeListener(event -> {
				if (!event.isOpened()) {
					onRefresh.run();
				}
			});
			dialog.open();
		});

		Button delete = new Button("Delete", VaadinIcon.TRASH.create());
		delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
		delete.addClickListener(e -> openConfirmDeleteDialog(ad, repository, onRefresh));

		HorizontalLayout actions = new HorizontalLayout(edit, delete);
		actions.setSpacing(true);

		add(title, description, meta, actions);
	}

	private void openConfirmDeleteDialog(Advertisement ad, AdvertisementRepository repository, Runnable onRefresh) {
		Dialog dialog = new Dialog();
		dialog.add(new Span("Delete advertisement \"" + ad.getTitle() + "\" (ID " + ad.getId() + ")?"));

		Button confirm = new Button("Delete", e -> {
			try {
				repository.delete(ad);
				Notification.show("Advertisement deleted", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				onRefresh.run();
			} catch (Exception ex) {
				Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button("Cancel", e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}
}
