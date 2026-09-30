package org.ost.feedback.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.feedback.services.FeedbackService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Pure delegation to {@link FeedbackService} -- no business logic of its own, per this project's
 *  {@code *PortImpl} convention. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackPortImpl implements FeedbackPort {

    private final FeedbackService service;

    @Override
    public List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        return service.findForEntity(entityType, entityId, page, size);
    }

    @Override
    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.count(entityType, entityId);
    }

    @Override
    @Transactional
    public FeedbackDto save(@NonNull FeedbackSaveDto dto) {
        return service.save(dto);
    }

    @Override
    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.getAggregate(entityType, entityId);
    }
}
