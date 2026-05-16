package org.ost.advertisement.attachment.spi;

import java.util.Map;
import java.util.Set;

public interface AttachmentEntityDisplayNameResolver {

    Map<Long, String> resolveDisplayNames(Set<Long> entityIds);
}
