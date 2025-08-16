package org.ost.advertisement.repository.advertisement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.RepositoryCustom.FieldRelations.SqlDtoFieldRelation;
import org.ost.advertisement.repository.advertisement.AdvertisementRepositoryCustomImpl.AdvertisementMapper.AdvertisementFieldRelations;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl extends
	RepositoryCustom<AdvertisementView, AdvertisementFilter> implements AdvertisementRepositoryCustom {

	private static final AdvertisementMapper ADVERTISEMENT_MAPPER = new AdvertisementMapper();
	private static final AdvertisementFieldConditionsRules ADVERTISEMENT_CONDITIONS_RULES = new AdvertisementFieldConditionsRules();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_MAPPER, ADVERTISEMENT_CONDITIONS_RULES);
	}

	@Override
	public List<AdvertisementView> findByFilter(AdvertisementFilter filter, Pageable pageable) {
		return super.findByFilter(filter, pageable);
	}

	@Override
	public Long countByFilter(AdvertisementFilter filter) {
		return super.countByFilter(filter);
	}

	private static class AdvertisementFieldConditionsRules extends FieldConditionsRules<AdvertisementFilter> {

		@AllArgsConstructor
		public enum FilterFieldRelations implements Relation {
			TITLE("title", AdvertisementFieldRelations.TITLE),
			CREATED_AT_START("createdAt_start", AdvertisementFieldRelations.CREATED_AT),
			CREATED_AT_END("createdAt_end", AdvertisementFieldRelations.CREATED_AT),
			UPDATED_AT_START("updatedAt_start", AdvertisementFieldRelations.UPDATED_AT),
			UPDATED_AT_END("updatedAt_end", AdvertisementFieldRelations.UPDATED_AT);
			@Getter
			private final String filterField;
			@Getter
			private final SqlDtoFieldRelation sqlDtoFieldRelation;
		}


		AdvertisementFieldConditionsRules() {
			super(EnumSet.allOf(FilterFieldRelations.class));
		}

		public String apply(MapSqlParameterSource params, AdvertisementFilter filter) {
			FieldConditions<AdvertisementFilter> fieldConditions = new FieldConditions<>(filter);
			for (Relation relation : relations) {
				switch (relation) {
					case FilterFieldRelations.TITLE -> like(relation, filter.getTitle(), fieldConditions);
					case FilterFieldRelations.CREATED_AT_START ->
						after(relation, filter.getCreatedAtStart(), fieldConditions);
					case FilterFieldRelations.CREATED_AT_END ->
						before(relation, filter.getCreatedAtEnd(), fieldConditions);
					case FilterFieldRelations.UPDATED_AT_START ->
						after(relation, filter.getUpdatedAtStart(), fieldConditions);
					case FilterFieldRelations.UPDATED_AT_END ->
						before(relation, filter.getUpdatedAtEnd(), fieldConditions);
					default -> throw new IllegalStateException("Unexpected value: " + relation);
				}
				;
			}
			params.addValues(fieldConditions.toParams());
			return fieldConditions.toSqlApplyingAnd();
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
			super(EnumSet.allOf(AdvertisementFieldRelations.class), """
				    advertisement a
				    LEFT JOIN user_information u ON a.user_id = u.id
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

