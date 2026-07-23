package org.ost.marketplace.ui.views.main.tabs.timeline.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.marketplace.ui.query.elements.fields.UserPickerField;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_ACTION_TYPE;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_DATE_END;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_DATE_START;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_ENTITY_TYPE;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_TIME_END;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_TIME_START;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_SORT_ACTOR;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_SORT_CREATED_AT;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S2065")
public class TimelineQueryBlock extends QueryBlock<AuditTimelineFilterDto> {

    @Getter
    private final transient FilterProcessor<AuditTimelineFilterDto>                 filterProcessor;
    @Getter
    @Qualifier("timelineSortProcessor")
    private final transient SortProcessor                                           sortProcessor;

    private final transient AccessEvaluator                                            access;
    private final transient I18nService                                                i18nService;
    private final transient UiComponentFactory<UserPickerField>                        userPickerFactory;

    @Getter
    private QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        queryActionBlock = new QueryActionBlock(i18nService);
        addClassName("timeline-query-block");
        setVisible(false);

        // Entity type row
        QueryMultiSelectComboField<EntityType> entityTypeField =
                new QueryMultiSelectComboField<>(i18nService.get(TIMELINE_FILTER_ENTITY_TYPE), EntityType.values());
        filterRow(i18nService.get(TIMELINE_FILTER_ENTITY_TYPE), entityTypeField, TimelineFilterMeta.ENTITY_TYPES);

        // Action type row
        QueryMultiSelectComboField<ActionType> actionTypeField =
                new QueryMultiSelectComboField<>(i18nService.get(TIMELINE_FILTER_ACTION_TYPE), ActionType.values());
        filterRow(i18nService.get(TIMELINE_FILTER_ACTION_TYPE), actionTypeField, TimelineFilterMeta.ACTION_TYPES);

        // Date row
        QueryDateTimeField fromDateField = new QueryDateTimeField(
                i18nService.get(TIMELINE_FILTER_DATE_START), i18nService.get(TIMELINE_FILTER_TIME_START), false);
        QueryDateTimeField toDateField = new QueryDateTimeField(
                i18nService.get(TIMELINE_FILTER_DATE_END), i18nService.get(TIMELINE_FILTER_TIME_END), true);
        filterRow(i18nService, i18nService.get(TIMELINE_SORT_CREATED_AT), fromDateField, toDateField,
                TimelineSortMeta.CREATED_AT, TimelineFilterMeta.FROM_DATE, TimelineFilterMeta.TO_DATE);

        // Actor row (admin/mod only)
        if (access.canView()) {
            UserPickerField actorField = userPickerFactory.get();
            QueryInlineRow actorRow = new QueryInlineRow(i18nService.get(TIMELINE_SORT_ACTOR), actorField);
            add(actorRow);
            filterProcessor.register(TimelineFilterMeta.ACTOR, actorField, queryActionBlock);
        }

        add(queryActionBlock);
    }
}
