package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_BUTTON_DELETE;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_BUTTON_EDIT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_CREATED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_UPDATED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_CARD_USER;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

public class AdvertisementCardView extends VerticalLayout {

	public AdvertisementCardView(AdvertisementInfoDto ad, Runnable onEdit, Runnable onDelete, I18nService i18n) {
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

		H3 title = new H3(ad.title());
		title.getStyle()
			.set("font-size", "1.2rem")
			.set("font-weight", "600")
			.set("margin", "0")
			.set("white-space", "nowrap")
			.set("overflow", "hidden")
			.set("text-overflow", "ellipsis");

		Span description = new Span(ad.description());
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
		toggle.addClickListener(e -> new AdvertisementDescriptionDialog(ad.title(), ad.description()).open());

		Span createdAt = new Span(
			i18n.get(ADVERTISEMENT_CARD_CREATED) + " " + TimeZoneUtil.formatInstant(ad.createdAt()));
		Span updatedAt = new Span(
			i18n.get(ADVERTISEMENT_CARD_UPDATED) + " " + TimeZoneUtil.formatInstant(ad.updatedAt()));
		Span userId = new Span(i18n.get(ADVERTISEMENT_CARD_USER) + " " + ad.createdByUserId());

		VerticalLayout meta = new VerticalLayout(createdAt, updatedAt, userId);
		meta.getStyle()
			.set("font-size", "0.85rem")
			.set("color", "#666")
			.set("gap", "4px")
			.set("padding", "0")
			.set("margin", "0");

		Button edit = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT), VaadinIcon.EDIT.create());
		edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		edit.addClickListener(e -> onEdit.run());

		Button delete = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE), VaadinIcon.TRASH.create());
		delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
		delete.addClickListener(e -> onDelete.run());

		HorizontalLayout actions = new HorizontalLayout(edit, delete);
		actions.getStyle()
			.set("justify-content", "flex-end")
			.set("gap", "8px");

		add(title, description, toggle, meta, actions);
	}
}
