package org.ost.sqlengine.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlNamingUtil {

    public static String toSnakeCase(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
