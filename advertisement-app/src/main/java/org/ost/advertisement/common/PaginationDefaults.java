package org.ost.advertisement.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaginationDefaults {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MIN_PAGE_SIZE     = 5;
    public static final int MAX_PAGE_SIZE     = 100;
}
