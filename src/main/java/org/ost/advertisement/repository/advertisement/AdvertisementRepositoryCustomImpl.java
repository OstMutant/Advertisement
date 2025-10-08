package org.ost.advertisement.repository.advertisement;

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
			ID("a.id", "id"),
			TITLE("a.title", "title"),
			DESCRIPTION("a.description", "description"),
			CREATED_AT("a.created_at", "createdAt"),
			UPDATED_AT("a.updated_at", "updatedAt"),
			USER_ID("u.id", "userId"),
			USER_NAME("u.name", "userName"),
			USER_EMAIL("u.email", "userEmail");

			@Getter
			private final String sqlField;

			@Getter
			private final String dtoField;
		}

		protected AdvertisementMapper() {
			super(AdvertisementFieldRelations.values(), """
				    advertisement a
				    LEFT JOIN user_information u ON a.created_by_user_id = u.id
				""");
		}

		@Override
		public AdvertisementView mapRow(ResultSet rs, int rowNum) throws SQLException {
			return AdvertisementView.builder()
				.id(rs.getObject(AdvertisementFieldRelations.ID.getDtoField(), Long.class))
				.title(rs.getString(AdvertisementFieldRelations.TITLE.getDtoField()))
				.description(rs.getString(AdvertisementFieldRelations.DESCRIPTION.getDtoField()))
				.createdAt(toInstant(rs.getTimestamp(AdvertisementFieldRelations.CREATED_AT.getDtoField())))
				.updatedAt(toInstant(rs.getTimestamp(AdvertisementFieldRelations.UPDATED_AT.getDtoField())))
				.userId(rs.getObject(AdvertisementFieldRelations.USER_ID.getDtoField(), Long.class))
				.userName(rs.getString(AdvertisementFieldRelations.USER_NAME.getDtoField()))
				.userEmail(rs.getString(AdvertisementFieldRelations.USER_EMAIL.getDtoField()))
				.build();
		}
	}
}

