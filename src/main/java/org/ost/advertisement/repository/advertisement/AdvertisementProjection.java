package org.ost.advertisement.repository.advertisement;

import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdAt;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserEmail;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserId;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserName;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.description;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.id;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.title;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.updatedAt;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.id;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.instant;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.repository.query.projection.SqlProjection;
import org.ost.advertisement.repository.query.projection.SqlFieldDefinition;

public class AdvertisementProjection extends SqlProjection<AdvertisementInfoDto> {

	public static final SqlFieldDefinition<Long> ID = id("a.id", id);
	public static final SqlFieldDefinition<String> TITLE = str("a.title", title);
	public static final SqlFieldDefinition<String> DESCRIPTION = str("a.description", description);
	public static final SqlFieldDefinition<Instant> CREATED_AT = instant("a.created_at", createdAt);
	public static final SqlFieldDefinition<Instant> UPDATED_AT = instant("a.updated_at", updatedAt);
	public static final SqlFieldDefinition<Long> USER_ID = id("u.id", createdByUserId);
	public static final SqlFieldDefinition<String> USER_NAME = str("u.name", createdByUserName);
	public static final SqlFieldDefinition<String> USER_EMAIL = str("u.email", createdByUserEmail);

	public AdvertisementProjection() {
		super(
			List.of(ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT, USER_ID, USER_NAME, USER_EMAIL),
			"""
					advertisement a
					LEFT JOIN user_information u ON a.created_by_user_id = u.id
				"""
		);
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
