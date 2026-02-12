package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.users.elements.query.rows.*;
import org.ost.advertisement.ui.views.users.meta.UserFilterMeta;
import org.ost.advertisement.ui.views.users.meta.UserSortMeta;
import org.ost.advertisement.ui.views.users.processor.UserFilterProcessor;
import org.ost.advertisement.ui.views.users.processor.UserSortProcessor;

@SpringComponent
@UIScope
@AllArgsConstructor
@Getter
public class UserQueryBlock extends VerticalLayout implements QueryBlock<UserFilterDto>, QueryBlockLayout {

    private final transient UserFilterProcessor filterProcessor;
    private final transient UserSortProcessor sortProcessor;

    private final UserQueryNameRow userQueryNameRow;
    private final UserQueryEmailRow userQueryEmailRow;
    private final UserQueryRoleRow userQueryRoleRow;

    private final UserQueryIdRow userQueryIdRow;
    private final UserQueryCreatedDateRow userQueryCreatedDateRow;
    private final UserQueryUpdatedDateRow userQueryUpdatedDateRow;

    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        addClassName("user-query-block");
        setVisible(false);

        add(userQueryIdRow, userQueryNameRow, userQueryEmailRow, userQueryRoleRow, userQueryCreatedDateRow, userQueryUpdatedDateRow, queryActionBlock);

        registerSorts();
        registerFilters();
    }

    private void registerSorts() {
        sortProcessor.register(UserSortMeta.ID, userQueryIdRow.getSortIcon(), queryActionBlock);
        sortProcessor.register(UserSortMeta.NAME, userQueryNameRow.getSortIcon(), queryActionBlock);
        sortProcessor.register(UserSortMeta.EMAIL, userQueryEmailRow.getSortIcon(), queryActionBlock);
        sortProcessor.register(UserSortMeta.ROLE, userQueryRoleRow.getSortIcon(), queryActionBlock);
        sortProcessor.register(UserSortMeta.CREATED_AT, userQueryCreatedDateRow.getSortIcon(), queryActionBlock);
        sortProcessor.register(UserSortMeta.UPDATED_AT, userQueryUpdatedDateRow.getSortIcon(), queryActionBlock);
    }

    private void registerFilters() {
        filterProcessor.register(UserFilterMeta.ID_MIN, userQueryIdRow.getMinField(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.ID_MAX, userQueryIdRow.getMaxField(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.NAME, userQueryNameRow.getFilterField(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.EMAIL, userQueryEmailRow.getFilterField(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.ROLE, userQueryRoleRow.getRoleField(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_START, userQueryCreatedDateRow.getStartDate(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.CREATED_AT_END, userQueryCreatedDateRow.getEndDate(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_START, userQueryUpdatedDateRow.getStartDate(), queryActionBlock);
        filterProcessor.register(UserFilterMeta.UPDATED_AT_END, userQueryUpdatedDateRow.getEndDate(), queryActionBlock);
    }

    @Override
    public boolean toggleVisibility() {
        setVisible(!isVisible());
        return isVisible();
    }
}

