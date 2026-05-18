package org.ost.platform.attachment.event;

public record AdvertisementRestoredEvent(Long adId, int snapshotVersion, Long userId) {}
