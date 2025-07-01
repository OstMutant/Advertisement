package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.WebBrowser;
import com.vaadin.flow.server.VaadinSession;
import org.ost.advertisement.ui.util.TimeZoneUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;

@SpringComponent
@UIScope
@PageTitle("Advertisements | Advertisement App")
@Route("advertisements")
@Slf4j
public class AdvertisementListView extends VerticalLayout {

	private final AdvertisementRepository advertisementRepository;
	private Grid<Advertisement> advertisementGrid;

	@Autowired
	public AdvertisementListView(AdvertisementRepository advertisementRepository) {
		this.advertisementRepository = advertisementRepository;
		addClassName("advertisement-list-view");
		setSizeFull();
		configureGrid();
		add(advertisementGrid);
	}

	@PostConstruct
	private void init() {
		CallbackDataProvider<Advertisement, Void> callbackDataProvider = DataProvider.fromCallbacks(
			query -> {
				Sort sort = Sort.by("id").ascending();
				PageRequest pageable = PageRequest.of(query.getOffset() / query.getLimit(), query.getLimit(), sort);
				return advertisementRepository.findByFilter(new AdvertisementFilter(), pageable).stream();
			},
			query -> {
				return advertisementRepository.countByFilter(new AdvertisementFilter()).intValue();
			}
		);

		advertisementGrid.setDataProvider(callbackDataProvider);
		advertisementGrid.getDataProvider().refreshAll();
	}

	private void configureGrid() {
		advertisementGrid = new Grid<>(Advertisement.class, false);
		advertisementGrid.setSizeFull();

		ZoneId clientZoneId = ZoneId.of(TimeZoneUtil.getClientTimeZoneId());

		advertisementGrid.addColumn(Advertisement::getId).setHeader("ID").setTextAlign(ColumnTextAlign.END);
		advertisementGrid.addColumn(Advertisement::getTitle).setHeader("Title").setFlexGrow(1);
		advertisementGrid.addColumn(Advertisement::getCategory).setHeader("Category");
		advertisementGrid.addColumn(Advertisement::getLocation).setHeader("Location");
		advertisementGrid.addColumn(Advertisement::getStatus).setHeader("Status");
		advertisementGrid.addColumn(advertisement -> formatInstant(advertisement.getCreatedAt(), clientZoneId)).setHeader("Created At");
		advertisementGrid.addColumn(advertisement -> formatInstant(advertisement.getUpdatedAt(), clientZoneId)).setHeader("Updated At");
		advertisementGrid.addColumn(Advertisement::getUserId).setHeader("User ID").setTextAlign(ColumnTextAlign.END);
	}

	private String formatInstant(Instant instant, ZoneId zoneId) {
		if (instant == null) {
			return "N/A";
		}
		return LocalDateTime.ofInstant(instant, zoneId)
			.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	public DataProvider<Advertisement, ?> getDataProvider() {
		return advertisementGrid.getDataProvider();
	}
}
