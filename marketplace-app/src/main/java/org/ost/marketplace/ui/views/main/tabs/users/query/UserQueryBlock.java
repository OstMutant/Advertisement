package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.Role;
import org.ost.query.ui.QueryBlock;
import org.ost.platform.ui.ComponentFactory;
import org.ost.query.ui.elements.SortIcon;
import org.ost.query.ui.elements.action.QueryActionBlock;
import org.ost.query.ui.elements.fields.QueryDateTimeField;
import org.ost.query.ui.elements.fields.QueryMultiSelectComboField;
import org.ost.query.ui.elements.fields.QueryNumberField;
import org.ost.query.ui.elements.fields.QueryTextField;
import org.ost.query.ui.elements.rows.QueryInlineRow;
import org.ost.query.ui.filter.FilterProcessor;
import org.ost.query.ui.sort.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserQueryBlock extends QueryBlock<UserFilterDto> {

    @Getter
    private final transient FilterProcessor<UserFilterDto> filterProcessor;
    @Getter
    @Qualifier("userSortProcessor")
    private final transient SortProcessor                  sortProcessor;

    private final transient ComponentFactory componentFactory;

    @Getter
    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        addClassName("user-query-block");
        setVisible(false);

        // Id row
        QueryNumberField idMinField = componentFactory.build(QueryNumberField.class,
                QueryNumberField.Parameters.builder().placeholderKey(USER_FILTER_ID_MIN).build());
        QueryNumberField idMaxField = componentFactory.build(QueryNumberField.class,
                QueryNumberField.Parameters.builder().placeholderKey(USER_FILTER_ID_MAX).build());
        SortIcon idSort = componentFactory.get(SortIcon.class);
        QueryInlineRow idRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_ID).sortIcon(idSort)
                        .filterField(idMinField).filterField(idMaxField).build());

        // Name row
        QueryTextField nameField = componentFactory.build(QueryTextField.class,
                QueryTextField.Parameters.builder().placeholderKey(USER_FILTER_NAME_PLACEHOLDER).build());
        SortIcon nameSort = componentFactory.get(SortIcon.class);
        QueryInlineRow nameRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_NAME).sortIcon(nameSort).filterField(nameField).build());

        // Email row
        QueryTextField emailField = componentFactory.build(QueryTextField.class,
                QueryTextField.Parameters.builder().placeholderKey(USER_FILTER_EMAIL_PLACEHOLDER).build());
        SortIcon emailSort = componentFactory.get(SortIcon.class);
        QueryInlineRow emailRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_EMAIL).sortIcon(emailSort).filterField(emailField).build());

        // Role row
        @SuppressWarnings("unchecked")
        QueryMultiSelectComboField<Role> roleField = componentFactory.build(
                (Class<QueryMultiSelectComboField<Role>>) (Class<?>) QueryMultiSelectComboField.class,
                QueryMultiSelectComboField.Parameters.<Role>builder()
                        .placeholderKey(USER_FILTER_ROLE_ANY).items(Role.values()).build());
        SortIcon roleSort = componentFactory.get(SortIcon.class);
        QueryInlineRow roleRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_ROLE).sortIcon(roleSort).filterField(roleField).build());

        // Created date row
        QueryDateTimeField createdStart = componentFactory.build(QueryDateTimeField.class,
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_CREATED_START)
                        .timePlaceholderKey(USER_FILTER_TIME_CREATED_START).build());
        QueryDateTimeField createdEnd = componentFactory.build(QueryDateTimeField.class,
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_CREATED_END)
                        .timePlaceholderKey(USER_FILTER_TIME_CREATED_END).isEnd(true).build());
        SortIcon createdSort = componentFactory.get(SortIcon.class);
        QueryInlineRow createdRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_CREATED).sortIcon(createdSort)
                        .filterField(createdStart).filterField(createdEnd).build());

        // Updated date row
        QueryDateTimeField updatedStart = componentFactory.build(QueryDateTimeField.class,
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_UPDATED_START)
                        .timePlaceholderKey(USER_FILTER_TIME_UPDATED_START).build());
        QueryDateTimeField updatedEnd = componentFactory.build(QueryDateTimeField.class,
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(USER_FILTER_DATE_UPDATED_END)
                        .timePlaceholderKey(USER_FILTER_TIME_UPDATED_END).isEnd(true).build());
        SortIcon updatedSort = componentFactory.get(SortIcon.class);
        QueryInlineRow updatedRow = componentFactory.build(QueryInlineRow.class,
                QueryInlineRow.Parameters.builder()
                        .labelTranslationKey(USER_SORT_UPDATED).sortIcon(updatedSort)
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