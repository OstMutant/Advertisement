package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.attachment.repository.PhotoActivityProjection;
import org.ost.attachment.service.PhotoSnapshotService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class UserActivityExtensionImpl implements UserActivityExtension {

    private final JdbcClient          jdbcClient;
    private final PhotoSnapshotService photoSnapshotService;

    @Override
    public List<ActivityItemDto> getPhotoActivity(Long userId) {
        PhotoActivityProjection projection = new PhotoActivityProjection(photoSnapshotService);
        return projection.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));
    }
}
