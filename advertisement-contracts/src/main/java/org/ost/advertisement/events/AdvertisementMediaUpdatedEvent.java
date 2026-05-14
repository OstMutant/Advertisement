package org.ost.advertisement.events;

public record AdvertisementMediaUpdatedEvent(Long adId, String mainImageUrl, String mainContentType, Integer imageCount) {}
