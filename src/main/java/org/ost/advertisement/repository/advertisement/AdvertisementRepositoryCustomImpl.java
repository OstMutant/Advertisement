package org.ost.advertisement.repository.advertisement;

import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.id;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.instant;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.AdvertisementInfoDto.Fields;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinition;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl
	extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
	implements AdvertisementRepositoryCustom {

	private static final AdvertisementMapper ADVERTISEMENT_MAPPER = new AdvertisementMapper();
	private static final AdvertisementFilterApplier ADVERTISEMENT_FILTER_APPLIER = new AdvertisementFilterApplier();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_MAPPER, ADVERTISEMENT_FILTER_APPLIER);
	}

	private static class AdvertisementFilterApplier extends FilterApplier<AdvertisementFilterDto> {

		public AdvertisementFilterApplier() {
			relations.addAll(List.of(
				of("title", AdvertisementMapper.TITLE, (f, fc, r) -> r.like(f.getTitle(), fc)),
				of("createdAt_start", AdvertisementMapper.CREATED_AT, (f, fc, r) -> r.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", AdvertisementMapper.CREATED_AT, (f, fc, r) -> r.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", AdvertisementMapper.UPDATED_AT, (f, fc, r) -> r.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", AdvertisementMapper.UPDATED_AT, (f, fc, r) -> r.before(f.getUpdatedAtEnd(), fc))
			));
		}
	}

	public static class AdvertisementMapper extends FieldRelations<AdvertisementInfoDto> {

		public static final SqlDtoFieldDefinition<Long> ID = id(Fields.id, "a.id");
		public static final SqlDtoFieldDefinition<String> TITLE = str(Fields.title, "a.title");
		public static final SqlDtoFieldDefinition<String> DESCRIPTION = str(Fields.description, "a.description");
		public static final SqlDtoFieldDefinition<Instant> CREATED_AT = instant(Fields.createdAt, "a.created_at");
		public static final SqlDtoFieldDefinition<Instant> UPDATED_AT = instant(Fields.updatedAt, "a.updated_at");
		public static final SqlDtoFieldDefinition<Long> USER_ID = id(Fields.createdByUserId, "u.id");
		public static final SqlDtoFieldDefinition<String> USER_NAME = str(Fields.createdByUserName, "u.name");
		public static final SqlDtoFieldDefinition<String> USER_EMAIL = str(Fields.createdByUserEmail, "u.email");

		public AdvertisementMapper() {
			super(new SqlDtoFieldDefinition<?>[]{
					ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT, USER_ID, USER_NAME, USER_EMAIL
				},
				"""
					    advertisement a
					    LEFT JOIN user_information u ON a.created_by_user_id = u.id
					""");
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

}
