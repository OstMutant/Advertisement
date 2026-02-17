package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.query.filter.FilterBuilder;

import java.util.List;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.*;
import static org.ost.advertisement.repository.advertisement.AdvertisementProjection.*;
import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.query.filter.SqlCondition.*;

public class AdvertisementFilterBuilder extends FilterBuilder<AdvertisementFilterDto> {

    public AdvertisementFilterBuilder() {
        relations.addAll(List.of(
                of(title, TITLE, (mapping, value) -> like(mapping, value.getTitle())),
                of(createdAtStart, CREATED_AT, (mapping, value) -> after(mapping, value.getCreatedAtStart())),
                of(createdAtEnd, CREATED_AT, (mapping, value) -> before(mapping, value.getCreatedAtEnd())),
                of(updatedAtStart, UPDATED_AT, (mapping, value) -> after(mapping, value.getUpdatedAtStart())),
                of(updatedAtEnd, UPDATED_AT, (mapping, value) -> before(mapping, value.getUpdatedAtEnd()))
        ));
    }
}
