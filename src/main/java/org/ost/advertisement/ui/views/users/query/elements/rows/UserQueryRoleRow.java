package org.ost.advertisement.ui.views.users.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryInlineRow;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryRoleField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_ROLE;

@SpringComponent
@UIScope
@Getter
public class UserQueryRoleRow extends QueryInlineRow {

    private final SortIcon sortIcon;
    private final UserQueryRoleField roleField;

    public UserQueryRoleRow(I18nService i18n,
                            SortIcon sortIcon,
                            UserQueryRoleField roleField) {
        super(i18n, USER_SORT_ROLE);
        this.sortIcon = sortIcon;
        this.roleField = roleField;
    }

    @jakarta.annotation.PostConstruct
    private void initLayout() {
        initLayout(sortIcon, roleField);
    }
}
