package org.ost.advertisement.attachment.event;

public record AdvertisementMediaUpdatedEvent(Long adId, String mediaUrl, String mediaContentType, Integer mediaCount) {}
