package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.ost.advertisement.ui.views.users.elements.*;
import org.ost.advertisement.ui.views.users.meta.UserFilterMeta;
import org.ost.advertisement.ui.views.users.processor.UserFilterProcessor;
import org.ost.advertisement.ui.views.users.processor.UserSortProcessor;

@SpringComponent
@UIScope
@AllArgsConstructor
@Getter
public class UserQueryBlock implements QueryBlock<UserFilterDto> {


    private final QueryActionBlock queryActionBlock;
    private final UserFilterProcessor filterProcessor;
    private final UserSortProcessor sortProcessor;

    private final UserQueryNameField nameField;
    private final UserQueryEmailField emailField;
    private final UserQueryRoleField roleCombo;

    private final UserQueryIdVerticalLayout idFilter;
    private final UserQueryCreatedVerticalLayout createdFilter;
    private final UserQueryUpdatedVerticalLayout updatedFilter;

    private final SortIcon idSortIcon;
    private final SortIcon nameSortIcon;
    private final SortIcon emailSortIcon;
    private final SortIcon roleSortIcon;
    private final SortIcon createdSortIcon;
    private final SortIcon updatedSortIcon;

    @PostConstruct
    private void initLayout() {
        sortProcessor.register(User.Fields.id, idSortIcon, queryActionBlock);
        sortProcessor.register(User.Fields.name, nameSortIcon, queryActionBlock);
        sortProcessor.register(User.Fields.email, emailSortIcon, queryActionBlock);
        sortProcessor.register(User.Fields.role, roleSortIcon, queryActionBlock);
        sortProcessor.register(User.Fields.createdAt, createdSortIcon, queryActionBlock);
        sortProcessor.register(User.Fields.updatedAt, updatedSortIcon, queryActionBlock);

        filterProcessor.register(UserFilterMeta.ID_MIN, idFilter.getIdMin(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.ID_MAX, idFilter.getIdMax(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.NAME, nameField, queryActionBlock);
        filterProcessor.register(UserFilterMeta.EMAIL, emailField, queryActionBlock);
        filterProcessor.register(UserFilterMeta.ROLE, roleCombo, queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_START, createdFilter.getCreatedStart(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_END, createdFilter.getCreatedEnd(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_START, updatedFilter.getUpdatedStart(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_END, updatedFilter.getUpdatedEnd(), queryActionBlock);
    }
}
