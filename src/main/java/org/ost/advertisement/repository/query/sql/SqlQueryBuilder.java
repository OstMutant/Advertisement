package org.ost.advertisement.repository.query.sql;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public class SqlQueryBuilder {

	public String select(String fields, String source) {
		return concat(
			"SELECT " + normalizeFields(fields),
			"FROM " + source
		);
	}

	public String select(String fields, String source, String where) {
		return concat(
			select(fields, source),
			wrap("WHERE ", where)
		);
	}

	public String select(String fields, String source, String where, String orderBy, String limit) {
		return concat(
			select(fields, source, where),
			wrap("ORDER BY ", orderBy),
			wrap("LIMIT ", limit)
		);
	}

	public String count(String source, String where) {
		return concat(
			"SELECT COUNT(*)",
			"FROM " + source,
			wrap("WHERE ", where)
		);
	}

	private String normalizeFields(String fields) {
		return StringUtils.isNotBlank(fields) ? fields : "*";
	}

	private String wrap(String prefix, String part) {
		return StringUtils.isNotBlank(part) ? prefix + part : "";
	}

	private String concat(String... parts) {
		return String.join(" ",
			Arrays.stream(parts)
				.filter(StringUtils::isNotBlank)
				.toList());
	}
}
