package org.ost.advertisement.repository.user;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserTable {

    public static final String TABLE = "user_information";
    public static final String ALIAS = "u";

    public static final String ID          = ALIAS + ".id";
    public static final String NAME        = ALIAS + ".name";
    public static final String EMAIL       = ALIAS + ".email";
    public static final String ROLE        = ALIAS + ".role";
    public static final String PASSWORD    = ALIAS + ".password_hash";
    public static final String CREATED_AT  = ALIAS + ".created_at";
    public static final String UPDATED_AT  = ALIAS + ".updated_at";
    public static final String LOCALE      = ALIAS + ".locale";

    public static final String SOURCE = TABLE + " " + ALIAS;
}