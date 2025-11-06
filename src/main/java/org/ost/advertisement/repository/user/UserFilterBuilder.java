package org.ost.advertisement.repository.user;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.email;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.endId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.name;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.role;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.startId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.repository.query.filter.SqlCondition.after;
import static org.ost.advertisement.repository.query.filter.SqlCondition.before;
import static org.ost.advertisement.repository.query.filter.SqlCondition.equalsTo;
import static org.ost.advertisement.repository.query.filter.SqlCondition.like;
import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.user.UserProjection.CREATED_AT;
import static org.ost.advertisement.repository.user.UserProjection.EMAIL;
import static org.ost.advertisement.repository.user.UserProjection.ID;
import static org.ost.advertisement.repository.user.UserProjection.NAME;
import static org.ost.advertisement.repository.user.UserProjection.ROLE;
import static org.ost.advertisement.repository.user.UserProjection.UPDATED_AT;

import java.util.List;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.repository.query.filter.FilterBuilder;

public class UserFilterBuilder extends FilterBuilder<UserFilterDto> {

	public UserFilterBuilder() {
		relations.addAll(List.of(
			of(name, NAME, (projection, value) -> like(projection, value.getName())),
			of(email, EMAIL, (projection, value) -> like(projection, value.getEmail())),
			of(role, ROLE,
				(projection, value) -> equalsTo(projection, value.getRole() != null ? value.getRole().name() : null)),
			of(createdAtStart, CREATED_AT, (projection, value) -> after(projection, value.getCreatedAtStart())),
			of(createdAtEnd, CREATED_AT, (projection, value) -> before(projection, value.getCreatedAtEnd())),
			of(updatedAtStart, UPDATED_AT, (projection, value) -> after(projection, value.getUpdatedAtStart())),
			of(updatedAtEnd, UPDATED_AT, (projection, value) -> before(projection, value.getUpdatedAtEnd())),
			of(startId, ID, (projection, value) -> after(projection, value.getStartId())),
			of(endId, ID, (projection, value) -> before(projection, value.getEndId()))
		));
	}
}
