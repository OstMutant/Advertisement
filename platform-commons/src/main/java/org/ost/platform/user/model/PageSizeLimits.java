package org.ost.platform.user.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Shared page-size bounds for per-user paginated settings (advertisements/users lists). */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PageSizeLimits {

    public static final int MIN_PAGE_SIZE     = 5;
    public static final int MAX_PAGE_SIZE     = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
}
