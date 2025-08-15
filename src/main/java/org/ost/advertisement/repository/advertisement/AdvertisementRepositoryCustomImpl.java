package org.ost.advertisement.repository.advertisement;

import java.sql.ResultSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.SneakyThrows;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.AdvertisementView.AdvertisementViewBuilder;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl extends RepositoryCustom implements AdvertisementRepositoryCustom {

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc);
	}

	@Override
	public List<AdvertisementView> findByFilter(AdvertisementFilter filter, Pageable pageable) {
		return findByFilter(AdvertisementFieldRelations.SOURCE, AdvertisementFieldRelations.getFieldMap(),
			params -> AdvertisementFieldConditions.applyALL(params, filter),
			pageable, (rs, rowNum) -> AdvertisementFieldRelations.transform(rs));
	}

	@Override
	public Long countByFilter(AdvertisementFilter filter) {
		return countByFilter(AdvertisementFieldRelations.SOURCE,
			params -> AdvertisementFieldConditions.applyALL(params, filter));
	}

	private enum AdvertisementFieldConditions implements FieldConditions<AdvertisementFilter> {
		TITLE(AdvertisementFieldRelations.TITLE.getField(), "title"),
		CREATED_AT_START(AdvertisementFieldRelations.CREATED_AT.getField(), "createdAt_start"),
		CREATED_AT_END(AdvertisementFieldRelations.CREATED_AT.getField(), "createdAt_end"),
		UPDATED_AT_START(AdvertisementFieldRelations.UPDATED_AT.getField(), "updatedAt_start"),
		UPDATED_AT_END(AdvertisementFieldRelations.UPDATED_AT.getField(), "updatedAt_end");

		private static final Set<AdvertisementFieldConditions> ALL = EnumSet.allOf(AdvertisementFieldConditions.class);

		public static String applyALL(MapSqlParameterSource params, AdvertisementFilter filter) {
			return FieldConditions.applyAllThroughtAnd(params, filter, ALL);
		}

		@Getter
		private final String field;

		@Getter
		private final String variable;

		AdvertisementFieldConditions(String field, String variable) {
			this.field = field;
			this.variable = variable;
		}

		@Override
		public String apply(MapSqlParameterSource params, AdvertisementFilter filter) {
			return switch (this) {
				case TITLE -> applyFullLike(params, filter.getTitle());
				case CREATED_AT_START -> applyTimestamp(params, " >= :", filter.getCreatedAtStart());
				case CREATED_AT_END -> applyTimestamp(params, " <= :", filter.getCreatedAtEnd());
				case UPDATED_AT_START -> applyTimestamp(params, " >= :", filter.getUpdatedAtStart());
				case UPDATED_AT_END -> applyTimestamp(params, " <= :", filter.getUpdatedAtEnd());
			};
		}
	}

	private enum AdvertisementFieldRelations implements FieldRelations<AdvertisementViewBuilder> {
		ID("a.id", "id"),
		TITLE("a.title", "title"),
		DESCRIPTION("a.description", "description"),
		CREATED_AT("a.created_at", "createdAt"),
		UPDATED_AT("a.updated_at", "updatedAt"),
		USER_ID("u.id", "userId"),
		USER_NAME("u.name", "userName"),
		USER_EMAIL("u.email", "userEmail");

		private static final String SOURCE = """
			    advertisement a
			    LEFT JOIN user_information u ON a.user_id = u.id
			""";

		private static final Set<? extends FieldRelations<AdvertisementViewBuilder>> ALL = EnumSet.allOf(AdvertisementFieldRelations.class);

		public static Map<String, String> getFieldMap() {
			return FieldRelations.getFieldMap( ALL);
		}

		public static AdvertisementView transform(ResultSet rs) {
			return FieldRelations.transform(rs, AdvertisementView.builder(), ALL).build();
		}

		@Getter
		private final String field;

		@Getter
		private final String alias;

		AdvertisementFieldRelations(String field, String alias) {
			this.field = field;
			this.alias = alias;
		}

		@SneakyThrows
		@Override
		public AdvertisementViewBuilder apply(ResultSet rs, AdvertisementViewBuilder b) {
			return switch (this) {
				case ID -> b.id(rs.getObject("id", Long.class));
				case TITLE -> b.title(rs.getString("title"));
				case DESCRIPTION -> b.description(rs.getString("description"));
				case CREATED_AT -> b.createdAt(toInstant(rs.getTimestamp("createdAt")));
				case UPDATED_AT -> b.updatedAt(toInstant(rs.getTimestamp("updatedAt")));
				case USER_ID -> b.userId(rs.getObject("userId", Long.class));
				case USER_NAME -> b.userName(rs.getString("userName"));
				case USER_EMAIL -> b.userEmail(rs.getString("userEmail"));
			};
		}
	}
}

