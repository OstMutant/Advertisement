package org.ost.advertisement.ui.views.advertisements.query.sort.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.ui.views.advertisements.query.sort.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class AdvertisementSortProcessor extends SortProcessor {

    public AdvertisementSortProcessor() {
        super(AdvertisementSortMeta.defaultSort());
    }
}
