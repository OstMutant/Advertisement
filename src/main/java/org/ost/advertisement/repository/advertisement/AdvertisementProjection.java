package org.ost.advertisement.repository.advertisement;

import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.repository.query.projection.SqlFieldDefinition;
import org.ost.advertisement.repository.query.projection.SqlProjection;
import org.ost.advertisement.repository.user.UserTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.*;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.*;

public class AdvertisementProjection extends SqlProjection<AdvertisementInfoDto> {

    public static final SqlFieldDefinition<Long>    ID         = id(AdvertisementTable.ID,          id);
    public static final SqlFieldDefinition<String>  TITLE      = str(AdvertisementTable.TITLE,      title);
    public static final SqlFieldDefinition<String>  DESCRIPTION= str(AdvertisementTable.DESCRIPTION,description);
    public static final SqlFieldDefinition<Instant> CREATED_AT = instant(AdvertisementTable.CREATED_AT, createdAt);
    public static final SqlFieldDefinition<Instant> UPDATED_AT = instant(AdvertisementTable.UPDATED_AT, updatedAt);
    public static final SqlFieldDefinition<Long>    USER_ID    = id(UserTable.ID,                   createdByUserId);
    public static final SqlFieldDefinition<String>  USER_NAME  = str(UserTable.NAME,                createdByUserName);
    public static final SqlFieldDefinition<String>  USER_EMAIL = str(UserTable.EMAIL,               createdByUserEmail);

    public AdvertisementProjection() {
        super(List.of(ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT, USER_ID, USER_NAME, USER_EMAIL),
                AdvertisementTable.SOURCE);
    }

    @Override
    public AdvertisementInfoDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return AdvertisementInfoDto.builder()
                .id(ID.extract(rs))
                .title(TITLE.extract(rs))
                .description(DESCRIPTION.extract(rs))
                .createdAt(CREATED_AT.extract(rs))
                .updatedAt(UPDATED_AT.extract(rs))
                .createdByUserId(USER_ID.extract(rs))
                .createdByUserName(USER_NAME.extract(rs))
                .createdByUserEmail(USER_EMAIL.extract(rs))
                .build();
    }
}