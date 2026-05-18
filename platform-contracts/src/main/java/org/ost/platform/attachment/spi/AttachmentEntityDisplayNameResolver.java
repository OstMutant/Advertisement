package org.ost.platform.attachment.spi;

import java.util.Map;
import java.util.Set;

public interface AttachmentEntityDisplayNameResolver {

    Map<Long, String> resolveDisplayNames(Set<Long> entityIds);
}
