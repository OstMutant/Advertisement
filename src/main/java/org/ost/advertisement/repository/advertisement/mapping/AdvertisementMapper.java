package org.ost.advertisement.repository.advertisement.mapping;

import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdAt;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserEmail;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserId;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdByUserName;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.description;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.id;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.title;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.updatedAt;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.id;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.instant;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinition;

public class AdvertisementMapper extends FieldRelations<AdvertisementInfoDto> {

	public static final SqlDtoFieldDefinition<Long> ID = id(id, "a.id");
	public static final SqlDtoFieldDefinition<String> TITLE = str(title, "a.title");
	public static final SqlDtoFieldDefinition<String> DESCRIPTION = str(description, "a.description");
	public static final SqlDtoFieldDefinition<Instant> CREATED_AT = instant(createdAt, "a.created_at");
	public static final SqlDtoFieldDefinition<Instant> UPDATED_AT = instant(updatedAt, "a.updated_at");
	public static final SqlDtoFieldDefinition<Long> USER_ID = id(createdByUserId, "u.id");
	public static final SqlDtoFieldDefinition<String> USER_NAME = str(createdByUserName, "u.name");
	public static final SqlDtoFieldDefinition<String> USER_EMAIL = str(createdByUserEmail, "u.email");

	public AdvertisementMapper() {
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
