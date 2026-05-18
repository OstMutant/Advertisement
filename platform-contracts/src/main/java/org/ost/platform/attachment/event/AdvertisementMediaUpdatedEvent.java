package org.ost.platform.attachment.event;

public record AdvertisementMediaUpdatedEvent(Long adId, String mediaUrl, String mediaContentType, Integer mediaCount) {}
