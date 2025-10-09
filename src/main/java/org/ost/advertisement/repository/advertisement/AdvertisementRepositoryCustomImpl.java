package org.ost.advertisement.repository.advertisement;

import jakarta.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.advertisement.AdvertisementRepositoryCustomImpl.AdvertisementMapper.AdvertisementFieldRelations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl extends RepositoryCustom<AdvertisementView, AdvertisementFilter>
	implements AdvertisementRepositoryCustom {

	private static final AdvertisementMapper ADVERTISEMENT_MAPPER = new AdvertisementMapper();
	private static final AdvertisementFilterApplier ADVERTISEMENT_CONDITIONS_RULES = new AdvertisementFilterApplier();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_MAPPER, ADVERTISEMENT_CONDITIONS_RULES);
	}

	private static class AdvertisementFilterApplier extends FilterApplier<AdvertisementFilter> {

		public AdvertisementFilterApplier() {
			relations.addAll(List.of(
				of("title", AdvertisementFieldRelations.TITLE,
					(f, fc, self) -> self.like(f.getTitle(), fc)),

				of("createdAt_start", AdvertisementFieldRelations.CREATED_AT,
					(f, fc, self) -> self.after(f.getCreatedAtStart(), fc)),

				of("createdAt_end", AdvertisementFieldRelations.CREATED_AT,
					(f, fc, self) -> self.before(f.getCreatedAtEnd(), fc)),

				of("updatedAt_start", AdvertisementFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.after(f.getUpdatedAtStart(), fc)),

				of("updatedAt_end", AdvertisementFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.before(f.getUpdatedAtEnd(), fc))
			));
		}

		@Override
		public String apply(MapSqlParameterSource params, AdvertisementFilter filter) {
			return applyRelations(params, filter);
		}
	}

	public static class AdvertisementMapper extends FieldRelations<AdvertisementView> {

		@AllArgsConstructor
		public enum AdvertisementFieldRelations implements SqlDtoFieldRelation {
			ID("a.id", "id", (rs, alias) -> rs.getObject(alias, Long.class)),
			TITLE("a.title", "title", ResultSet::getString),
			DESCRIPTION("a.description", "description", ResultSet::getString),
			CREATED_AT("a.created_at", "createdAt", (rs, alias) -> toInstant(rs.getTimestamp(alias))),
			UPDATED_AT("a.updated_at", "updatedAt", (rs, alias) -> toInstant(rs.getTimestamp(alias))),
			USER_ID("u.id", "userId", (rs, alias) -> rs.getObject(alias, Long.class)),
			USER_NAME("u.name", "userName", ResultSet::getString),
			USER_EMAIL("u.email", "userEmail", ResultSet::getString);

			@Getter
			private final String sqlField;

			@Getter
			private final String dtoField;

			@Getter
			private final ValueExtractor<ResultSet, String, ?> extractorLogic;
		}

		protected AdvertisementMapper() {
			super(AdvertisementFieldRelations.values(), """
				    advertisement a
				    LEFT JOIN user_information u ON a.created_by_user_id = u.id
				""");
		}

		@Override
		public AdvertisementView mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
			return AdvertisementView.builder()
				.id(AdvertisementFieldRelations.ID.extract(rs))
				.title(AdvertisementFieldRelations.TITLE.extract(rs))
				.description(AdvertisementFieldRelations.DESCRIPTION.extract(rs))
				.createdAt(AdvertisementFieldRelations.CREATED_AT.extract(rs))
				.updatedAt(AdvertisementFieldRelations.UPDATED_AT.extract(rs))
				.userId(AdvertisementFieldRelations.USER_ID.extract(rs))
				.userName(AdvertisementFieldRelations.USER_NAME.extract(rs))
				.userEmail(AdvertisementFieldRelations.USER_EMAIL.extract(rs))
				.build();
		}
	}
}

