package org.ost.advertisement.ui.views.tabs.users.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryDateTimeField;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryMultiSelectComboField;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryNumberField;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryTextField;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryInlineRow;
import org.ost.advertisement.ui.views.components.query.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.processor.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserQueryBlock extends QueryBlock<UserFilterDto> {

    @Getter
    private final transient FilterProcessor<UserFilterDto> filterProcessor;
    @Getter
    @Qualifier("userSortProcessor")
    private final transient SortProcessor                  sortProcessor;

    private final transient QueryTextField.Builder                   textFieldBuilder;
    private final transient QueryNumberField.Builder                 numberFieldBuilder;
    private final transient QueryDateTimeField.Builder               dateTimeFieldBuilder;
    private final transient QueryMultiSelectComboField.Builder<Role> multiComboBuilder;
    private final transient QueryInlineRow.Builder                   rowBuilder;
    private final transient SortIcon.Builder                         sortIconBuilder;

    @Getter
    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        addClassName("user-query-block");
        setVisible(false);

        // Id row
        QueryNumberField idMinField = numberFieldBuilder.build(QueryNumberField.Parameters.builder()
                .placeholderKey(USER_FILTER_ID_MIN).build());
        QueryNumberField idMaxField = numberFieldBuilder.build(QueryNumberField.Parameters.builder()
                .placeholderKey(USER_FILTER_ID_MAX).build());
        SortIcon idSort = sortIconBuilder.build();
        QueryInlineRow idRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_ID).sortIcon(idSort)
                .filterField(idMinField).filterField(idMaxField).build());

        // Name row
        QueryTextField nameField = textFieldBuilder.build(QueryTextField.Parameters.builder()
                .placeholderKey(USER_FILTER_NAME_PLACEHOLDER).build());
        SortIcon nameSort = sortIconBuilder.build();
        QueryInlineRow nameRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_NAME).sortIcon(nameSort).filterField(nameField).build());

        // Email row
        QueryTextField emailField = textFieldBuilder.build(QueryTextField.Parameters.builder()
                .placeholderKey(USER_FILTER_EMAIL_PLACEHOLDER).build());
        SortIcon emailSort = sortIconBuilder.build();
        QueryInlineRow emailRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_EMAIL).sortIcon(emailSort).filterField(emailField).build());

        // Role row
        QueryMultiSelectComboField<Role> roleField = multiComboBuilder.build(
                QueryMultiSelectComboField.Parameters.<Role>builder()
                        .placeholderKey(USER_FILTER_ROLE_ANY).items(Role.values()).build());
        SortIcon roleSort = sortIconBuilder.build();
        QueryInlineRow roleRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_ROLE).sortIcon(roleSort).filterField(roleField).build());

        // Created date row
        QueryDateTimeField createdStart = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(USER_FILTER_DATE_CREATED_START)
                .timePlaceholderKey(USER_FILTER_TIME_CREATED_START).build());
        QueryDateTimeField createdEnd = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(USER_FILTER_DATE_CREATED_END)
                .timePlaceholderKey(USER_FILTER_TIME_CREATED_END).isEnd(true).build());
        SortIcon createdSort = sortIconBuilder.build();
        QueryInlineRow createdRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_CREATED).sortIcon(createdSort)
                .filterField(createdStart).filterField(createdEnd).build());

        // Updated date row
        QueryDateTimeField updatedStart = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(USER_FILTER_DATE_UPDATED_START)
                .timePlaceholderKey(USER_FILTER_TIME_UPDATED_START).build());
        QueryDateTimeField updatedEnd = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(USER_FILTER_DATE_UPDATED_END)
                .timePlaceholderKey(USER_FILTER_TIME_UPDATED_END).isEnd(true).build());
        SortIcon updatedSort = sortIconBuilder.build();
        QueryInlineRow updatedRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(USER_SORT_UPDATED).sortIcon(updatedSort)
                .filterField(updatedStart).filterField(updatedEnd).build());

        add(idRow, nameRow, emailRow, roleRow, createdRow, updatedRow, queryActionBlock);

        sortProcessor.register(UserSortMeta.ID,         idSort,      queryActionBlock);
        sortProcessor.register(UserSortMeta.NAME,       nameSort,    queryActionBlock);
        sortProcessor.register(UserSortMeta.EMAIL,      emailSort,   queryActionBlock);
        sortProcessor.register(UserSortMeta.ROLE,       roleSort,    queryActionBlock);
        sortProcessor.register(UserSortMeta.CREATED_AT, createdSort, queryActionBlock);
        sortProcessor.register(UserSortMeta.UPDATED_AT, updatedSort, queryActionBlock);

        filterProcessor.register(UserFilterMeta.ID_MIN,           idMinField,   queryActionBlock);
        filterProcessor.register(UserFilterMeta.ID_MAX,           idMaxField,   queryActionBlock);
        filterProcessor.register(UserFilterMeta.NAME,             nameField,    queryActionBlock);
        filterProcessor.register(UserFilterMeta.EMAIL,            emailField,   queryActionBlock);
        filterProcessor.register(UserFilterMeta.ROLES,            roleField,    queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_START, createdStart, queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_END,   createdEnd,   queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_START, updatedStart, queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_END,   updatedEnd,   queryActionBlock);
    }

}