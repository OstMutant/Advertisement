package org.ost.marketplace.ui.core;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.user.model.PageSizeLimits;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaginationDefaults {

    public static final int DEFAULT_PAGE_SIZE = PageSizeLimits.DEFAULT_PAGE_SIZE;
    public static final int MIN_PAGE_SIZE     = PageSizeLimits.MIN_PAGE_SIZE;
    public static final int MAX_PAGE_SIZE     = PageSizeLimits.MAX_PAGE_SIZE;
}
