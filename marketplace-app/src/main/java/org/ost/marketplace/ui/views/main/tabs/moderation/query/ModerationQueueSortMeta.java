package org.ost.marketplace.ui.views.main.tabs.moderation.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Metadata definitions for moderation queue sort fields. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModerationQueueSortMeta {

    public static final SortFieldMeta CREATED_AT  = SortFieldMeta.of(ModerationQueueItemDto.Fields.createdAt,  MODERATION_SORT_CREATED_AT);
    public static final SortFieldMeta TYPE        = SortFieldMeta.of(ModerationQueueItemDto.Fields.type,       MODERATION_SORT_TYPE);
}
