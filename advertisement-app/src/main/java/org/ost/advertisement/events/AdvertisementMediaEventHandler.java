package org.ost.advertisement.events;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.attachment.event.AdvertisementMediaUpdatedEvent;
import org.ost.advertisement.repository.advertisement.AdvertisementDescriptor;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisementMediaEventHandler {

    private static final SqlWriteCommand UPDATE_MEDIA = SqlWriteCommand.of(
            "UPDATE " + AdvertisementDescriptor.Write.TABLE +
            " SET " + AdvertisementDescriptor.Write.MEDIA_URL          + " = :url," +
            " "     + AdvertisementDescriptor.Write.MEDIA_CONTENT_TYPE + " = :contentType," +
            " "     + AdvertisementDescriptor.Write.MEDIA_COUNT        + " = :count" +
            " WHERE id = :id"
    );

    private final JdbcClient jdbcClient;

    @EventListener
    public void onMediaUpdated(AdvertisementMediaUpdatedEvent event) {
        UPDATE_MEDIA.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("url",         event.mediaUrl())
                        .addValue("contentType", event.mediaContentType())
                        .addValue("count",       event.mediaCount())
                        .addValue("id",          event.adId()));
    }
}
