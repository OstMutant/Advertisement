package org.ost.advertisement.repository.query.sql;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public class SqlQueryBuilder {

	public String buildSelect(String fields, String source) {
		return join(
			"SELECT " + defaultFields(fields),
			"FROM " + source
		);
	}

	public String buildSelect(String fields, String source, String where) {
		return join(
			buildSelect(fields, source),
			prefix(where, "WHERE ")
		);
	}

	public String buildSelect(String fields, String source, String where, String sort, String limit) {
		return join(
			buildSelect(fields, source, where),
			prefix(sort, ""),
			prefix(limit, "")
		);
	}

	public String buildCount(String source, String where) {
		return join(
			"SELECT COUNT(*)",
			"FROM " + source,
			prefix(where, "WHERE ")
		);
	}

	private String defaultFields(String fields) {
		return StringUtils.isNotBlank(fields) ? fields : "*";
	}

	private String prefix(String part, String prefix) {
		return StringUtils.isNotBlank(part) ? prefix + part : "";
	}

	private String join(String... parts) {
		return String.join(" ",
			Arrays.stream(parts)
				.filter(StringUtils::isNotBlank)
				.toList());
	}
}
