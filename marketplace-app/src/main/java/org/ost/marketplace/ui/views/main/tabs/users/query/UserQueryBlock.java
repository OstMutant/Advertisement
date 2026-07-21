package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.model.Role;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryLongField;
import org.ost.marketplace.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
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

    private final transient UiComponentFactory<QueryTextField>                    textFieldFactory;
    private final transient UiComponentFactory<QueryLongField>                    longFieldFactory;
    private final transient UiComponentFactory<QueryDateTimeField>                dateTimeFieldFactory;
    private final transient UiComponentFactory<QueryMultiSelectComboField<Role>>  roleComboFactory;
    private final transient UiComponentFactory<QueryInlineRow>                    inlineRowFactory;
    private final transient UiComponentFactory<SortIcon>                          sortIconFactory;

    @Getter
    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        addClassName("user-query-block");
        setVisible(false);

        // Id row
        QueryLongField idMinField = longFieldFactory.build(
                QueryLongField.Parameters.builder()
                        .placeholderKey(USER_FILTER_ID_MIN).invalidNumberMessageKey(USER_FILTER_ID_INVALID_NUMBER).build());
        QueryLongField idMaxField = longFieldFactory.build(
                QueryLongField.Parameters.builder()
                        .placeholderKey(USER_FILTER_ID_MAX).invalidNumberMessageKey(USER_FILTER_ID_INVALID_NUMBER).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_ID, idMinField, idMaxField,
                UserSortMeta.ID, UserFilterMeta.ID_MIN, UserFilterMeta.ID_MAX);

        // Name row
        QueryTextField nameField = textFieldFactory.build(
                QueryTextField.Parameters.builder().placeholderKey(USER_FILTER_NAME_PLACEHOLDER).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_NAME, nameField, UserSortMeta.NAME, UserFilterMeta.NAME);

        // Email row
        QueryTextField emailField = textFieldFactory.build(
                QueryTextField.Parameters.builder().placeholderKey(USER_FILTER_EMAIL_PLACEHOLDER).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_EMAIL, emailField, UserSortMeta.EMAIL, UserFilterMeta.EMAIL);

        // Role row
        QueryMultiSelectComboField<Role> roleField = roleComboFactory.build(
                QueryMultiSelectComboField.Parameters.<Role>builder()
                        .placeholderKey(USER_FILTER_ROLE_ANY).items(Role.values()).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_ROLE, roleField, UserSortMeta.ROLE, UserFilterMeta.ROLES);

        // Created date row
        QueryDateTimeField createdStart = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_CREATED_START)
                        .timePlaceholderKey(USER_FILTER_TIME_CREATED_START).build());
        QueryDateTimeField createdEnd = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_CREATED_END)
                        .timePlaceholderKey(USER_FILTER_TIME_CREATED_END).isEnd(true).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_CREATED, createdStart, createdEnd,
                UserSortMeta.CREATED_AT, UserFilterMeta.CREATED_AT_START, UserFilterMeta.CREATED_AT_END);

        // Updated date row
        QueryDateTimeField updatedStart = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_UPDATED_START)
                        .timePlaceholderKey(USER_FILTER_TIME_UPDATED_START).build());
        QueryDateTimeField updatedEnd = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_UPDATED_END)
                        .timePlaceholderKey(USER_FILTER_TIME_UPDATED_END).isEnd(true).build());
        filterRow(inlineRowFactory, sortIconFactory,
                USER_SORT_UPDATED, updatedStart, updatedEnd,
                UserSortMeta.UPDATED_AT, UserFilterMeta.UPDATED_AT_START, UserFilterMeta.UPDATED_AT_END);

        add(queryActionBlock);
    }

}