package org.ost.advertisement.attachment.event;

public record AdvertisementRestoredEvent(Long adId, int snapshotVersion, Long userId) {}
