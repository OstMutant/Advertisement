package org.ost.advertisement.repository.user.filter;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.email;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.endId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.name;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.role;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.startId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.repository.query.filter.SimpleFilterRelation.of;
import static org.ost.advertisement.repository.user.mapping.UserProjection.CREATED_AT;
import static org.ost.advertisement.repository.user.mapping.UserProjection.EMAIL;
import static org.ost.advertisement.repository.user.mapping.UserProjection.ID;
import static org.ost.advertisement.repository.user.mapping.UserProjection.NAME;
import static org.ost.advertisement.repository.user.mapping.UserProjection.ROLE;
import static org.ost.advertisement.repository.user.mapping.UserProjection.UPDATED_AT;

import java.util.List;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.repository.query.filter.FilterApplier;

public class UserFilterApplier extends FilterApplier<UserFilterDto> {

	public UserFilterApplier() {
		relations.addAll(List.of(
			of(name, NAME, (f, fc, r) -> r.like(f.getName(), fc)),
			of(email, EMAIL, (f, fc, r) -> r.like(f.getEmail(), fc)),
			of(role, ROLE, (f, fc, r) -> r.equalsTo(f.getRole() != null ? f.getRole().name() : null, fc)),
			of(createdAtStart, CREATED_AT, (f, fc, r) -> r.after(f.getCreatedAtStart(), fc)),
			of(createdAtEnd, CREATED_AT, (f, fc, r) -> r.before(f.getCreatedAtEnd(), fc)),
			of(updatedAtStart, UPDATED_AT, (f, fc, r) -> r.after(f.getUpdatedAtStart(), fc)),
			of(updatedAtEnd, UPDATED_AT, (f, fc, r) -> r.before(f.getUpdatedAtEnd(), fc)),
			of(startId, ID, (f, fc, r) -> r.after(f.getStartId(), fc)),
			of(endId, ID, (f, fc, r) -> r.before(f.getEndId(), fc))
		));
	}
}
