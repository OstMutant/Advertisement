package org.ost.advertisement.repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.AdvertisementView.AdvertisementViewBuilder;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
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
		return countByFilter(AdvertisementFieldRelations.SOURCE, params -> AdvertisementFieldConditions.applyALL(params, filter));
	}

	private enum AdvertisementFieldConditions {
		TITLE(AdvertisementFieldRelations.TITLE.getField(), "title"),
		CREATED_AT_START(AdvertisementFieldRelations.CREATED_AT.getField(), "createdAt_start"),
		CREATED_AT_END(AdvertisementFieldRelations.CREATED_AT.getField(), "createdAt_end"),
		UPDATED_AT_START(AdvertisementFieldRelations.UPDATED_AT.getField(), "updatedAt_start"),
		UPDATED_AT_END(AdvertisementFieldRelations.UPDATED_AT.getField(), "updatedAt_end");

		private static final Set<AdvertisementFieldConditions> ALL = EnumSet.allOf(AdvertisementFieldConditions.class);

		public static String applyALL(MapSqlParameterSource params, AdvertisementFilter filter) {
			List<String> conditions = new ArrayList<>();
			for (AdvertisementFieldConditions condition : ALL) {
				String conditionStr = condition.apply(params, filter);
				if (conditionStr != null) {
					conditions.add(conditionStr);
				}
			}
			return String.join(" AND ", conditions);
		}

		@Getter
		private final String field;

		@Getter
		private final String variable;

		AdvertisementFieldConditions(String field, String variable) {
			this.field = field;
			this.variable = variable;
		}

		public String apply(MapSqlParameterSource params, AdvertisementFilter filter) {
			return switch (this) {
				case TITLE -> {
					String title = filter.getTitle();
					if (title != null && !title.isBlank()) {
						params.addValue(variable, title);
						yield field + " ILIKE '%' || :" + variable + " || '%'";
					}
					yield null;
				}
				case CREATED_AT_START -> {
					Timestamp createdAtStart = toTimestamp(filter.getCreatedAtStart());
					if (createdAtStart != null) {
						params.addValue(variable, createdAtStart);
						yield field + " >= :" + variable;
					}
					yield null;
				}
				case CREATED_AT_END -> {
					Timestamp createdAtEnd = toTimestamp(filter.getCreatedAtEnd());
					if (createdAtEnd != null) {
						params.addValue(variable, createdAtEnd);
						yield field + " <= :" + variable;
					}
					yield null;
				}
				case UPDATED_AT_START -> {
					Timestamp updatedAtStart = toTimestamp(filter.getUpdatedAtStart());
					if (updatedAtStart != null) {
						params.addValue(variable, updatedAtStart);
						yield field + " >= :" + variable;
					}
					yield null;
				}
				case UPDATED_AT_END -> {
					Timestamp updatedAtEnd = toTimestamp(filter.getUpdatedAtEnd());
					if (updatedAtEnd != null) {
						params.addValue(variable, updatedAtEnd);
						yield field + " <= :" + variable;
					}
					yield null;
				}
			};
		}
	}


	private enum AdvertisementFieldRelations {
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

		private static final Set<AdvertisementFieldRelations> ALL = EnumSet.allOf(AdvertisementFieldRelations.class);

		public static Map<String, String> getFieldMap() {
			return ALL.stream().collect(
				Collectors.toMap(AdvertisementFieldRelations::getAlias, AdvertisementFieldRelations::getField));
		}

		public static AdvertisementView transform(ResultSet rs) {
			AdvertisementViewBuilder builder = AdvertisementView.builder();
			for (AdvertisementFieldRelations field : ALL) {
				builder = field.apply(rs, builder);
			}
			return builder.build();
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

