package org.ost.advertisement.ui.views.advertisements.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class AdvertisementSortProcessor extends SortProcessor {

	public AdvertisementSortProcessor() {
		super(AdvertisementSortMeta.defaultSort());
	}
}
