package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityType;

public interface ActivityRowBinding {

    EntityType entityType();

    Component decorate(ActivityItemDto item);
}
