package org.ost.advertisement.ui.views.users.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

@SpringComponent
@Scope("prototype")
public class UserSortProcessor extends SortProcessor {

	public UserSortProcessor() {
		super(new CustomSort(Sort.by(
			Sort.Order.desc(User.Fields.updatedAt),
			Sort.Order.desc(User.Fields.createdAt)
		)));
	}
}
