package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.ui.query.filter.FilterMapper;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;

@Mapper(componentModel = "spring")
public interface TimelineFilterMapper extends FilterMapper<AuditTimelineFilterDto> {

    void update(@MappingTarget AuditTimelineFilterDto target, AuditTimelineFilterDto source);

    AuditTimelineFilterDto copy(AuditTimelineFilterDto source);
}
