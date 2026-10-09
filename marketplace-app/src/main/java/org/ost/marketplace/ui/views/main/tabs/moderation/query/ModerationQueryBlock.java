package org.ost.marketplace.ui.views.main.tabs.moderation.query;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S2065") // Vaadin Component is Serializable; transient excludes non-serializable Spring proxies
public class ModerationQueryBlock extends QueryBlock<ModerationQueueFilterDto> {

    @Getter
    private final transient FilterProcessor<ModerationQueueFilterDto> filterProcessor;
    @Getter
    @Qualifier("moderationSortProcessor")
    private final transient SortProcessor                             sortProcessor;

    @Getter
    private QueryActionBlock queryActionBlock;

    private final transient I18nService i18nService;

    @PostConstruct
    private void initLayout() {
        queryActionBlock = new QueryActionBlock(i18nService);
        addClassName("moderation-query-block");
        setVisible(false);

        // Type row
        MultiSelectComboBox<ModerationQueueItemType> typeField = new MultiSelectComboBox<>();
        typeField.setPlaceholder(i18nService.get(MODERATION_FILTER_TYPE));
        typeField.setItems(ModerationQueueItemType.values());
        typeField.getElement().setAttribute("data-testid", "moderation-filter-type");
        filterRow(i18nService, i18nService.get(MODERATION_SORT_TYPE), typeField,
                ModerationQueueSortMeta.TYPE, ModerationQueueFilterMeta.TYPES);

        // Created date row
        QueryDateTimeField createdStart = new QueryDateTimeField(
                i18nService.get(MODERATION_FILTER_DATE_CREATED_START),
                i18nService.get(MODERATION_FILTER_TIME_CREATED_START), false);
        QueryDateTimeField createdEnd = new QueryDateTimeField(
                i18nService.get(MODERATION_FILTER_DATE_CREATED_END),
                i18nService.get(MODERATION_FILTER_TIME_CREATED_END), true);
        filterRow(i18nService, i18nService.get(MODERATION_SORT_CREATED_AT), createdStart, createdEnd,
                ModerationQueueSortMeta.CREATED_AT,
                ModerationQueueFilterMeta.CREATED_AT_START, ModerationQueueFilterMeta.CREATED_AT_END);

        // Author row
        QueryTextField authorField = new QueryTextField(i18nService.get(MODERATION_FILTER_AUTHOR_PLACEHOLDER));
        authorField.getElement().setAttribute("data-testid", "moderation-filter-author");
        filterRow(i18nService, i18nService.get(MODERATION_SORT_AUTHOR), authorField,
                ModerationQueueSortMeta.AUTHOR_NAME, ModerationQueueFilterMeta.AUTHOR_NAME);

        // Reason row (filter-only, no sort)
        QueryTextField reasonField = new QueryTextField(i18nService.get(MODERATION_FILTER_REASON));
        reasonField.getElement().setAttribute("data-testid", "moderation-filter-reason");
        filterRow(i18nService.get(MODERATION_FILTER_REASON), reasonField, ModerationQueueFilterMeta.REASON_TEXT);

        // Status row (filter-only, no sort) -- HIDDEN (pending) and CONFIRMED_HIDDEN only; NEW never appears in this queue's source data
        MultiSelectComboBox<FeedbackModerationStatus> statusField = new MultiSelectComboBox<>();
        statusField.setPlaceholder(i18nService.get(MODERATION_FILTER_STATUS));
        statusField.setItems(FeedbackModerationStatus.HIDDEN, FeedbackModerationStatus.CONFIRMED_HIDDEN);
        statusField.getElement().setAttribute("data-testid", "moderation-filter-status");
        filterRow(i18nService.get(MODERATION_FILTER_STATUS), statusField, ModerationQueueFilterMeta.STATUSES);

        add(queryActionBlock);
    }
}
