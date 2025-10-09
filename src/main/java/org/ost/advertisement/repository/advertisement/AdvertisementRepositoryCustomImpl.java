package org.ost.advertisement.repository.advertisement;

import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.id;
import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.instant;
import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.meta.fields.SqlDtoFieldRelation;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl
	extends RepositoryCustom<AdvertisementView, AdvertisementFilter>
	implements AdvertisementRepositoryCustom {

	private static final AdvertisementMapper ADVERTISEMENT_MAPPER = new AdvertisementMapper();
	private static final AdvertisementFilterApplier ADVERTISEMENT_CONDITIONS_RULES = new AdvertisementFilterApplier();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_MAPPER, ADVERTISEMENT_CONDITIONS_RULES);
	}

	private static class AdvertisementFilterApplier extends FilterApplier<AdvertisementFilter> {

		public AdvertisementFilterApplier() {
			relations.addAll(List.of(
				of("title", Fields.TITLE, (f, fc, self) -> self.like(f.getTitle(), fc)),
				of("createdAt_start", Fields.CREATED_AT, (f, fc, self) -> self.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", Fields.CREATED_AT, (f, fc, self) -> self.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", Fields.UPDATED_AT, (f, fc, self) -> self.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", Fields.UPDATED_AT, (f, fc, self) -> self.before(f.getUpdatedAtEnd(), fc))
			));
		}
	}

	public static class AdvertisementMapper extends FieldRelations<AdvertisementView> {

		public AdvertisementMapper() {
			super(Fields.ALL, """
                advertisement a
                LEFT JOIN user_information u ON a.created_by_user_id = u.id
            """);
		}

		@Override
		public AdvertisementView mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
			return AdvertisementView.builder()
				.id(Fields.ID.extract(rs))
				.title(Fields.TITLE.extract(rs))
				.description(Fields.DESCRIPTION.extract(rs))
				.createdAt(Fields.CREATED_AT.extract(rs))
				.updatedAt(Fields.UPDATED_AT.extract(rs))
				.userId(Fields.USER_ID.extract(rs))
				.userName(Fields.USER_NAME.extract(rs))
				.userEmail(Fields.USER_EMAIL.extract(rs))
				.build();
		}
	}
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	private static class Fields {

		public static final SqlDtoFieldRelation<Long> ID = id("id", "a.id");
		public static final SqlDtoFieldRelation<String> TITLE = str("title", "a.title");
		public static final SqlDtoFieldRelation<String> DESCRIPTION = str("description", "a.description");
		public static final SqlDtoFieldRelation<Instant> CREATED_AT = instant("createdAt", "a.created_at");
		public static final SqlDtoFieldRelation<Instant> UPDATED_AT = instant("updatedAt", "a.updated_at");
		public static final SqlDtoFieldRelation<Long> USER_ID = id("userId", "u.id");
		public static final SqlDtoFieldRelation<String> USER_NAME = str("userName", "u.name");
		public static final SqlDtoFieldRelation<String> USER_EMAIL = str("userEmail", "u.email");

		public static final SqlDtoFieldRelation<?>[] ALL = {
			ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT, USER_ID, USER_NAME, USER_EMAIL
		};
	}
}
