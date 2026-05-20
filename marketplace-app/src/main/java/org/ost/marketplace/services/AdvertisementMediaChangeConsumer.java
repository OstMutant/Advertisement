package org.ost.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.repository.advertisement.AdvertisementDescriptor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaChangeConsumer;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisementMediaChangeConsumer implements MediaChangeConsumer {

    private static final SqlWriteCommand UPDATE_MEDIA = SqlWriteCommand.of(
            "UPDATE " + AdvertisementDescriptor.Write.TABLE +
            " SET " + AdvertisementDescriptor.Write.MEDIA_URL          + " = :url," +
            " "     + AdvertisementDescriptor.Write.MEDIA_CONTENT_TYPE + " = :contentType," +
            " "     + AdvertisementDescriptor.Write.MEDIA_COUNT        + " = :count" +
            " WHERE id = :id"
    );

    private final JdbcClient     jdbcClient;
    private final AttachmentPort attachmentPort;

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        MediaSummaryDto summary = attachmentPort.getMediaSummary(entityType, entityId);
        UPDATE_MEDIA.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("url",         summary.displayUrl())
                        .addValue("contentType", summary.contentType())
                        .addValue("count",       summary.count())
                        .addValue("id",          entityId));
    }
}
