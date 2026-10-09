package org.ost.marketplace.ui.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.ost.marketplace.ui.query.filter.FilterMapper;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;

@Mapper(componentModel = "spring")
public interface ModerationQueueFilterMapper extends FilterMapper<ModerationQueueFilterDto> {

    void update(@MappingTarget ModerationQueueFilterDto target, ModerationQueueFilterDto source);

    ModerationQueueFilterDto copy(ModerationQueueFilterDto source);
}
