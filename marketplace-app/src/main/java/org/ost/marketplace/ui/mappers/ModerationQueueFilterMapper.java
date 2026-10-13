package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.ui.query.filter.FilterMapper;
import org.ost.orchestrator.dto.ModerationQueueFilterDto;

/** MapStruct mapper between moderation filter UI form state and query DTO. */
@Mapper(componentModel = "spring")
public interface ModerationQueueFilterMapper extends FilterMapper<ModerationQueueFilterDto> {

    void update(@MappingTarget ModerationQueueFilterDto target, ModerationQueueFilterDto source);

    ModerationQueueFilterDto copy(ModerationQueueFilterDto source);
}
