package org.ost.advertisement.ui.views.users.query.sort.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;
import org.ost.advertisement.ui.views.users.query.sort.meta.UserSortMeta;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class UserSortProcessor extends SortProcessor {

    public UserSortProcessor() {
        super(UserSortMeta.defaultSort());
    }
}
