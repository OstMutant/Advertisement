package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_BUTTON_DELETE;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_BUTTON_EDIT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_CREATED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_UPDATED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_USER;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementDescriptionDialog;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class AdvertisementCardView extends VerticalLayout {

	private final transient I18nService i18n;
	private final transient AdvertisementService advertisementService;
	private final transient AdvertisementUpsertDialog upsertDialog;

	public AdvertisementCardView(I18nService i18n, AdvertisementService advertisementService, AdvertisementUpsertDialog upsertDialog) {
		this.i18n = i18n;
		this.advertisementService = advertisementService;
		this.upsertDialog = upsertDialog;
	}

	public AdvertisementCardView build(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
		addClassName("advertisement-card");
		getStyle()
			.set("border", "1px solid #ccc")
			.set("border-radius", "8px")
			.set("padding", "16px")
			.set("box-shadow", "2px 2px 6px rgba(0,0,0,0.05)")
			.set("max-width", "600px")
			.set("width", "100%")
			.set("box-sizing", "border-box")
			.set("display", "flex")
			.set("flex-direction", "column")
			.set("gap", "12px")
			.set("flex", "1 1 300px")
			.set("max-width", "400px");

		H3 title = new H3(ad.getTitle());
		title.getStyle()
			.set("font-size", "1.2rem")
			.set("font-weight", "600")
			.set("margin", "0")
			.set("white-space", "nowrap")
			.set("overflow", "hidden")
			.set("text-overflow", "ellipsis");

		Span description = new Span(ad.getDescription());
		description.getStyle()
			.set("display", "-webkit-box")
			.set("overflow", "hidden")
			.set("text-overflow", "ellipsis")
			.set("word-break", "break-word")
			.set("-webkit-line-clamp", "5")
			.set("-webkit-box-orient", "vertical")
			.set("white-space", "normal")
			.set("font-size", "0.95rem")
			.set("color", "#444")
			.set("max-height", "6.5em")
			.set("line-height", "1.3em");

		Button toggle = new Button("Read more");
		toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		toggle.getStyle().set("padding", "0");
		toggle.addClickListener(e -> new AdvertisementDescriptionDialog(ad.getTitle(), ad.getDescription()).open());

		Span createdAt = new Span(
			i18n.get(ADVERTISEMENT_CARD_CREATED) + " " + TimeZoneUtil.formatInstant(ad.getCreatedAt()));
		Span updatedAt = new Span(
			i18n.get(ADVERTISEMENT_CARD_UPDATED) + " " + TimeZoneUtil.formatInstant(ad.getUpdatedAt()));
		Span userId = new Span(i18n.get(ADVERTISEMENT_CARD_USER) + " " + ad.getCreatedByUserId());

		VerticalLayout meta = new VerticalLayout(createdAt, updatedAt, userId);
		meta.getStyle()
			.set("font-size", "0.85rem")
			.set("color", "#666")
			.set("gap", "4px")
			.set("padding", "0")
			.set("margin", "0");

		Button edit = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT), VaadinIcon.EDIT.create());
		edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		edit.addClickListener(e -> upsertDialog.openEdit(ad));

		Button delete = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE), VaadinIcon.TRASH.create());
		delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
		delete.addClickListener(e -> openConfirmDeleteDialog(ad, refreshAdvertisements));

		HorizontalLayout actions = new HorizontalLayout(edit, delete);
		actions.getStyle()
			.set("justify-content", "flex-end")
			.set("gap", "8px");

		add(title, description, toggle, meta, actions);

		return this;
	}

	private void openConfirmDeleteDialog(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
		ConfirmDeleteHelper.showConfirm(
			i18n,
			i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()),
			ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON,
			ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON,
			() -> {
				try {
					advertisementService.delete(ad);
					NotificationType.SUCCESS.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED));
					refreshAdvertisements.run();
				} catch (Exception ex) {
					NotificationType.ERROR.show(
						i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage())
					);
				}
			}
		);
	}
}
