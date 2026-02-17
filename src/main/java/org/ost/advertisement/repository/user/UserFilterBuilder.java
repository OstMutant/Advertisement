package org.ost.advertisement.repository.user;

import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.repository.query.filter.FilterBuilder;

import java.util.List;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.*;
import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.query.filter.SqlCondition.*;
import static org.ost.advertisement.repository.user.UserProjection.*;

public class UserFilterBuilder extends FilterBuilder<UserFilterDto> {

    public UserFilterBuilder() {
        relations.addAll(List.of(
                of(name, NAME, (projection, value) -> like(projection, value.getName())),
                of(email, EMAIL, (projection, value) -> like(projection, value.getEmail())),
                of(roles, ROLE, (projection, value) -> inSet(projection, value.getRoles())),
                of(createdAtStart, CREATED_AT, (projection, value) -> after(projection, value.getCreatedAtStart())),
                of(createdAtEnd, CREATED_AT, (projection, value) -> before(projection, value.getCreatedAtEnd())),
                of(updatedAtStart, UPDATED_AT, (projection, value) -> after(projection, value.getUpdatedAtStart())),
                of(updatedAtEnd, UPDATED_AT, (projection, value) -> before(projection, value.getUpdatedAtEnd())),
                of(startId, ID, (projection, value) -> after(projection, value.getStartId())),
                of(endId, ID, (projection, value) -> before(projection, value.getEndId()))
        ));
    }
}