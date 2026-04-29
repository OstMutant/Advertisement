package org.ost.advertisement.events;

public record AdvertisementMediaUpdatedEvent(Long adId, String mainImageUrl, Integer imageCount) {}
