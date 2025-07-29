package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.ui.components.PaginationBarModern;
import org.ost.advertisement.ui.views.sort.CustomSort;
import org.springframework.data.domain.PageRequest;

@PageTitle("Advertisements | Advertisement App")
@Route("advertisements")
public class AdvertisementListView extends VerticalLayout {

	private final AdvertisementRepository repository;
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final AdvertisementLeftSidebar sidebar;
	private final AdvertisementFilterFields filterFields;
	private final CustomSort customSort;
	private final VerticalLayout advertisementContainer = new VerticalLayout();

	public AdvertisementListView(AdvertisementRepository repository) {
		this.repository = repository;
		setSizeFull();
		setSpacing(true);
		setPadding(true);

		paginationBar.setPageSize(25);
		paginationBar.setPageChangeListener(e -> refreshAdvertisements());

		sidebar = new AdvertisementLeftSidebar(() -> {
			paginationBar.setTotalCount(0);
			refreshAdvertisements();
		});
		sidebar.hide();

		filterFields = sidebar.getFilterFields();
		customSort = sidebar.getCustomSort();

		Button toggleSidebarButton = new Button("☰ Toggle Sidebar", e -> sidebar.toggle());
		Button addAdvertisementButton = createAddButton();

		HorizontalLayout topBar = new HorizontalLayout(toggleSidebarButton, addAdvertisementButton);
		topBar.setWidthFull();
		topBar.setJustifyContentMode(JustifyContentMode.BETWEEN);

		VerticalLayout contentLayout = new VerticalLayout(advertisementContainer, paginationBar);
		contentLayout.setSizeFull();
		contentLayout.setSpacing(true);
		contentLayout.setPadding(false);
		contentLayout.setFlexGrow(1, advertisementContainer);

		HorizontalLayout mainLayout = new HorizontalLayout(sidebar, contentLayout);
		mainLayout.setSizeFull();
		mainLayout.setFlexGrow(1, contentLayout);

		add(topBar, mainLayout);
		refreshAdvertisements();
	}

	private void refreshAdvertisements() {
		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();
		PageRequest pageable = PageRequest.of(page, size, customSort.getSort());
		AdvertisementFilter currentFilter = filterFields.getNewFilter();
		List<Advertisement> pageData = repository.findByFilter(currentFilter, pageable);
		int totalCount = repository.countByFilter(currentFilter).intValue();

		paginationBar.setTotalCount(totalCount);
		advertisementContainer.removeAll();
		pageData.forEach(ad ->
			advertisementContainer.add(
				new AdvertisementCardView(ad, repository, this::refreshAdvertisements)
			)
		);
	}

	private Button createAddButton() {
		Button add = new Button("Add Advertisement");
		add.addClickListener(e -> openAdvertisementFormDialog(null));
		return add;
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, repository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		dialog.open();
	}
}
