package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.*;
import static org.ost.advertisement.repository.advertisement.AdvertisementDescriptor.*;
import static org.ost.sqlengine.filter.SqlBoundFilter.of;
import static org.ost.sqlengine.filter.SqlCondition.*;

public class AdvertisementFilterBuilder extends SqlFilterBuilder<AdvertisementFilterDto> {

    public AdvertisementFilterBuilder() {
        super(List.of(
                of(title, TITLE, (mapping, value) -> like(mapping, value.getTitle())),
                of(createdAtStart, CREATED_AT, (mapping, value) -> after(mapping, value.getCreatedAtStart())),
                of(createdAtEnd, CREATED_AT, (mapping, value) -> before(mapping, value.getCreatedAtEnd())),
                of(updatedAtStart, UPDATED_AT, (mapping, value) -> after(mapping, value.getUpdatedAtStart())),
                of(updatedAtEnd, UPDATED_AT, (mapping, value) -> before(mapping, value.getUpdatedAtEnd()))
        ));
    }

    @Override
    public String build(MapSqlParameterSource params, AdvertisementFilterDto filter) {
        String dynamic = super.build(params, filter);
        String base    = DELETED_AT.sqlExpression() + " IS NULL";
        return dynamic.isEmpty() ? base : base + " AND " + dynamic;
    }
}
