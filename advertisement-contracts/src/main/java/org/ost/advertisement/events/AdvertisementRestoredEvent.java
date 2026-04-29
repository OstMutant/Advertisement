package org.ost.advertisement.events;

public record AdvertisementRestoredEvent(Long adId, int snapshotVersion, Long userId) {}
