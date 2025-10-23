package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SIDEBAR_BUTTON_ADD;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;

@SpringComponent
@UIScope
public class AdvertisementLeftSidebar extends VerticalLayout {

	private final Button addAdvertisementButton;
	@Getter
	private final AdvertisementQueryBlock queryBlock;

	public AdvertisementLeftSidebar(AdvertisementQueryBlock queryBlock, I18nService i18n) {
		this.queryBlock = queryBlock;
		this.addAdvertisementButton = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
	}

	@PostConstruct
	private void init() {
		setPadding(true);
		setSpacing(true);
		setWidth("270px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		getStyle().set("background-color", "#f4f4f4");

		VerticalLayout layout = new VerticalLayout(addAdvertisementButton, queryBlock.getComponent());
		layout.setSpacing(true);
		layout.setPadding(false);

		add(layout);
	}

	public void eventProcessor(Runnable onRefreshAction, Runnable onAddButton) {
		queryBlock.eventProcessor(onRefreshAction);
		addAdvertisementButton.addClickListener(e -> onAddButton.run());
	}
}


