package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.ui.views.advertisements.elements.query.AdvertisementQueryCreatedDateRow;
import org.ost.advertisement.ui.views.advertisements.elements.query.AdvertisementQueryTitleRow;
import org.ost.advertisement.ui.views.advertisements.elements.query.AdvertisementQueryUpdatedDateRow;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementFilterProcessor;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementSortProcessor;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementQueryBlock extends VerticalLayout implements QueryBlock<AdvertisementFilterDto>,
        QueryBlockLayout {

    @Getter
    private final QueryActionBlock queryActionBlock;
    @Getter
    private final transient AdvertisementFilterProcessor filterProcessor;
    @Getter
    private final transient AdvertisementSortProcessor sortProcessor;

    private final transient AdvertisementQueryTitleRow advertisementQueryTitleRow;
    private final transient AdvertisementQueryCreatedDateRow advertisementQueryCreatedDateRow;
    private final transient AdvertisementQueryUpdatedDateRow advertisementQueryUpdatedDateRow;

    @PostConstruct
    private void initLayout() {
        initLayout(advertisementQueryTitleRow, advertisementQueryCreatedDateRow, advertisementQueryUpdatedDateRow,
                queryActionBlock);

        sortProcessor.register(AdvertisementSortMeta.TITLE, advertisementQueryTitleRow.getSortIcon(),
                queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.CREATED_AT, advertisementQueryCreatedDateRow.getSortIcon(),
                queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.UPDATED_AT, advertisementQueryUpdatedDateRow.getSortIcon(),
                queryActionBlock);

        filterProcessor.register(AdvertisementFilterMeta.TITLE, advertisementQueryTitleRow.getFilterField(),
                queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START,
                advertisementQueryCreatedDateRow.getStartDate(), queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END, advertisementQueryCreatedDateRow.getEndDate(),
                queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START,
                advertisementQueryUpdatedDateRow.getStartDate(), queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END, advertisementQueryUpdatedDateRow.getEndDate(),
                queryActionBlock);
    }

    public void initLayout(Component... components) {
        setPadding(false);
        setSpacing(false);
        setVisible(false);
        getStyle()
                .set("margin-top", "8px")
                .set("padding", "8px")
                .set("border", "1px solid #ddd")
                .set("border-radius", "6px")
                .set("background-color", "#fafafa")
                .set("gap", "6px");
        add(components);
    }

    @Override
    public boolean toggleVisibility() {
        boolean nowVisible = !this.isVisible();
        setVisible(nowVisible);
        return nowVisible;
    }
}
