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
			params -> applyConditions(params, filter),
			pageable, (rs, rowNum) -> AdvertisementFieldRelations.transform(rs));
	}

	@Override
	public Long countByFilter(AdvertisementFilter filter) {
		return countByFilter(AdvertisementFieldRelations.SOURCE, params -> applyConditions(params, filter));
	}

	private String applyConditions(MapSqlParameterSource params, AdvertisementFilter filter) {
		String title = filter.getTitle();
		Timestamp createdAtStart = toTimestamp(filter.getCreatedAtStart());
		Timestamp createdAtEnd = toTimestamp(filter.getCreatedAtEnd());
		Timestamp updatedAtStart = toTimestamp(filter.getUpdatedAtStart());
		Timestamp updatedAtEnd = toTimestamp(filter.getUpdatedAtEnd());
		List<String> sqlConditions = new ArrayList<>();
		if (title != null && !title.isBlank()) {
			params.addValue("title", title);
			sqlConditions.add("a.title ILIKE '%' || :title || '%'");
		}
		if (createdAtStart != null) {
			params.addValue("createdAt_start", createdAtStart);
			sqlConditions.add("a.created_at >= :createdAt_start");
		}
		if (createdAtEnd != null) {
			params.addValue("createdAt_end", createdAtEnd);
			sqlConditions.add("a.created_at <= :createdAt_end");
		}
		if (updatedAtStart != null) {
			params.addValue("updatedAt_start", updatedAtStart);
			sqlConditions.add("a.updated_at >= :updatedAt_start");
		}
		if (updatedAtEnd != null) {
			params.addValue("updatedAt_end", updatedAtEnd);
			sqlConditions.add("a.updated_at <= :updatedAt_end");
		}
		return String.join(" AND ", sqlConditions);
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
				Collectors.toMap(AdvertisementFieldRelations::getAlias, AdvertisementFieldRelations::getFieldPath));
		}

		public static AdvertisementView transform(ResultSet rs) {
			AdvertisementViewBuilder builder = AdvertisementView.builder();
			for (AdvertisementFieldRelations field : ALL) {
				builder = field.apply(rs, builder);
			}
			return builder.build();
		}

		@Getter
		private final String fieldPath;

		@Getter
		private final String alias;

		AdvertisementFieldRelations(String fieldPath, String alias) {
			this.fieldPath = fieldPath;
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

