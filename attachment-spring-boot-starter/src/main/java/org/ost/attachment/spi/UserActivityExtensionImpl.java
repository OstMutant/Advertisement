package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.attachment.repository.AttachmentActivityProjection;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class UserActivityExtensionImpl implements UserActivityExtension {

    private final JdbcClient               jdbcClient;
    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public List<ActivityItemDto> getMediaActivity(Long userId) {
        AttachmentActivityProjection projection = new AttachmentActivityProjection(attachmentSnapshotService);
        return projection.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));
    }
}
