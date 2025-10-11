package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_CARD_BUTTON_DELETE;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_CARD_BUTTON_EDIT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_CARD_CREATED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_CARD_UPDATED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_CARD_USER;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;

public class AdvertisementCardView extends VerticalLayout {

	public AdvertisementCardView(AdvertisementInfoDto ad, Runnable onEdit, Runnable onDelete, I18nService i18n) {
		addClassName("advertisement-card");
		getStyle().set("border", "1px solid #ccc");
		getStyle().set("border-radius", "8px");
		getStyle().set("padding", "16px");
		getStyle().set("box-shadow", "2px 2px 6px rgba(0,0,0,0.05)");

		H3 title = new H3(ad.title());
		title.getElement().setProperty("title", ad.title());

		Span description = new Span(ad.description());
		description.getStyle()
			.set("display", "-webkit-box")
			.set("overflow", "hidden")
			.set("text-overflow", "ellipsis")
			.set("word-break", "break-word")
			.set("-webkit-line-clamp", "3")
			.set("-webkit-box-orient", "vertical")
			.set("white-space", "normal");

		Span createdAt = new Span(i18n.get(ADVERTISEMENT_CARD_CREATED) + " " + formatInstant(ad.createdAt()));
		Span updatedAt = new Span(i18n.get(ADVERTISEMENT_CARD_UPDATED) + " " + formatInstant(ad.updatedAt()));
		Span userId = new Span(i18n.get(ADVERTISEMENT_CARD_USER) + " " + ad.createdByUserId());

		VerticalLayout meta = new VerticalLayout(createdAt, updatedAt, userId);
		meta.setSpacing(false);
		meta.setPadding(false);

		Button edit = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT), VaadinIcon.EDIT.create());
		edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		edit.addClickListener(e -> onEdit.run());

		Button delete = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE), VaadinIcon.TRASH.create());
		delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
		delete.addClickListener(e -> onDelete.run());

		HorizontalLayout actions = new HorizontalLayout(edit, delete);
		actions.setSpacing(true);

		add(title, description, meta, actions);
	}
}


