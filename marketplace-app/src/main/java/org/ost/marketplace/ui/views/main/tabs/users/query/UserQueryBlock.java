package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.model.Role;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryLongField;
import org.ost.marketplace.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserQueryBlock extends QueryBlock<UserFilterDto> {

    @Getter
    private final transient FilterProcessor<UserFilterDto> filterProcessor;
    @Getter
    @Qualifier("userSortProcessor")
    private final transient SortProcessor                  sortProcessor;

    private final transient I18nService                                            i18nService;

    @Getter
    private QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        queryActionBlock = new QueryActionBlock(i18nService);
        addClassName("user-query-block");
        setVisible(false);

        // Id row
        QueryLongField idMinField = new QueryLongField(
                i18nService.get(USER_FILTER_ID_MIN), i18nService.get(USER_FILTER_ID_INVALID_NUMBER));
        QueryLongField idMaxField = new QueryLongField(
                i18nService.get(USER_FILTER_ID_MAX), i18nService.get(USER_FILTER_ID_INVALID_NUMBER));
        filterRow(i18nService, i18nService.get(USER_SORT_ID), idMinField, idMaxField,
                UserSortMeta.ID, UserFilterMeta.ID_MIN, UserFilterMeta.ID_MAX);

        // Name row
        QueryTextField nameField = new QueryTextField(i18nService.get(USER_FILTER_NAME_PLACEHOLDER));
        filterRow(i18nService, i18nService.get(USER_SORT_NAME), nameField, UserSortMeta.NAME, UserFilterMeta.NAME);

        // Email row
        QueryTextField emailField = new QueryTextField(i18nService.get(USER_FILTER_EMAIL_PLACEHOLDER));
        filterRow(i18nService, i18nService.get(USER_SORT_EMAIL), emailField, UserSortMeta.EMAIL, UserFilterMeta.EMAIL);

        // Role row
        QueryMultiSelectComboField<Role> roleField =
                new QueryMultiSelectComboField<>(i18nService.get(USER_FILTER_ROLE_ANY), Role.values());
        filterRow(i18nService, i18nService.get(USER_SORT_ROLE), roleField, UserSortMeta.ROLE, UserFilterMeta.ROLES);

        // Created date row
        QueryDateTimeField createdStart = new QueryDateTimeField(
                i18nService.get(USER_FILTER_DATE_CREATED_START), i18nService.get(USER_FILTER_TIME_CREATED_START), false);
        QueryDateTimeField createdEnd = new QueryDateTimeField(
                i18nService.get(USER_FILTER_DATE_CREATED_END), i18nService.get(USER_FILTER_TIME_CREATED_END), true);
        filterRow(i18nService, i18nService.get(USER_SORT_CREATED), createdStart, createdEnd,
                UserSortMeta.CREATED_AT, UserFilterMeta.CREATED_AT_START, UserFilterMeta.CREATED_AT_END);

        // Updated date row
        QueryDateTimeField updatedStart = new QueryDateTimeField(
                i18nService.get(USER_FILTER_DATE_UPDATED_START), i18nService.get(USER_FILTER_TIME_UPDATED_START), false);
        QueryDateTimeField updatedEnd = new QueryDateTimeField(
                i18nService.get(USER_FILTER_DATE_UPDATED_END), i18nService.get(USER_FILTER_TIME_UPDATED_END), true);
        filterRow(i18nService, i18nService.get(USER_SORT_UPDATED), updatedStart, updatedEnd,
                UserSortMeta.UPDATED_AT, UserFilterMeta.UPDATED_AT_START, UserFilterMeta.UPDATED_AT_END);

        add(queryActionBlock);
    }

}