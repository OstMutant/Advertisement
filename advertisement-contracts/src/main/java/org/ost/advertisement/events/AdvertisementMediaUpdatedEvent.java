package org.ost.advertisement.events;

public record AdvertisementMediaUpdatedEvent(Long adId, String mediaUrl, String mediaContentType, Integer mediaCount) {}
