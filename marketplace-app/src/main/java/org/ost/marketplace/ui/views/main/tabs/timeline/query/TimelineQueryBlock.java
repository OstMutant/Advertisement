package org.ost.marketplace.ui.views.main.tabs.timeline.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.spi.UserPort;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryLazyComboField;
import org.ost.marketplace.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;

import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_ACTION_TYPE;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_ACTOR;
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
public class TimelineQueryBlock extends QueryBlock<AuditTimelineFilterDto> {

    @Getter
    private final transient FilterProcessor<AuditTimelineFilterDto> filterProcessor;
    @Getter
    @Qualifier("timelineSortProcessor")
    private final transient SortProcessor                           sortProcessor;

    private final transient AccessEvaluator                                            access;
    private final transient UserPort                                                   userPort;
    private final transient UiComponentFactory<QueryMultiSelectComboField<EntityType>> entityTypeComboFactory;
    private final transient UiComponentFactory<QueryMultiSelectComboField<ActionType>> actionTypeComboFactory;
    private final transient UiComponentFactory<QueryDateTimeField>                     dateTimeFieldFactory;
    @SuppressWarnings("rawtypes")
    private final transient UiComponentFactory<QueryLazyComboField>                    lazyComboFactory;
    private final transient UiComponentFactory<QueryInlineRow>                         inlineRowFactory;
    private final transient UiComponentFactory<SortIcon>                               sortIconFactory;

    @Getter
    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void initLayout() {
        addClassName("timeline-query-block");
        setVisible(false);

        // Entity type row
        QueryMultiSelectComboField<EntityType> entityTypeField = entityTypeComboFactory.build(
                QueryMultiSelectComboField.Parameters.<EntityType>builder()
                        .placeholderKey(TIMELINE_FILTER_ENTITY_TYPE).items(EntityType.values()).build());
        QueryInlineRow entityTypeRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(TIMELINE_FILTER_ENTITY_TYPE).filterField(entityTypeField).build());

        // Action type row
        QueryMultiSelectComboField<ActionType> actionTypeField = actionTypeComboFactory.build(
                QueryMultiSelectComboField.Parameters.<ActionType>builder()
                        .placeholderKey(TIMELINE_FILTER_ACTION_TYPE).items(ActionType.values()).build());
        QueryInlineRow actionTypeRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(TIMELINE_FILTER_ACTION_TYPE).filterField(actionTypeField).build());

        // Date row
        QueryDateTimeField fromDateField = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(TIMELINE_FILTER_DATE_START)
                        .timePlaceholderKey(TIMELINE_FILTER_TIME_START).build());
        QueryDateTimeField toDateField = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(TIMELINE_FILTER_DATE_END)
                        .timePlaceholderKey(TIMELINE_FILTER_TIME_END).isEnd(true).build());
        SortIcon dateSort = sortIconFactory.get();
        QueryInlineRow dateRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(TIMELINE_SORT_CREATED_AT).sortIcon(dateSort)
                        .filterField(fromDateField).filterField(toDateField).build());

        add(entityTypeRow, actionTypeRow, dateRow);

        // Actor row (admin/mod only)
        if (access.canView()) {
            QueryLazyComboField<UserDto> actorField = lazyComboFactory.build(
                    QueryLazyComboField.Parameters.<UserDto>builder()
                            .placeholderKey(TIMELINE_FILTER_ACTOR)
                            .labelGenerator(UserDto::name)
                            .fetchCallback(query -> userPort.getFiltered(
                                    UserFilterDto.builder().name(query.getFilter().orElse(null)).build(),
                                    query.getOffset(), query.getLimit(),
                                    Sort.by(Sort.Order.asc("name"))).stream())
                            .countCallback(query -> userPort.count(
                                    UserFilterDto.builder().name(query.getFilter().orElse(null)).build()))
                            .build());
            QueryInlineRow actorRow = inlineRowFactory.build(
                    QueryInlineRow.Parameters.builder()
                            .labelKey(TIMELINE_SORT_ACTOR).filterField(actorField).build());
            add(actorRow);
            filterProcessor.register(TimelineFilterMeta.ACTOR, actorField, queryActionBlock);
        }

        add(queryActionBlock);

        sortProcessor.register(TimelineSortMeta.CREATED_AT, dateSort, queryActionBlock);

        filterProcessor.register(TimelineFilterMeta.ENTITY_TYPES, entityTypeField, queryActionBlock);
        filterProcessor.register(TimelineFilterMeta.ACTION_TYPES, actionTypeField, queryActionBlock);
        filterProcessor.register(TimelineFilterMeta.FROM_DATE,    fromDateField,   queryActionBlock);
        filterProcessor.register(TimelineFilterMeta.TO_DATE,      toDateField,     queryActionBlock);
    }
}
