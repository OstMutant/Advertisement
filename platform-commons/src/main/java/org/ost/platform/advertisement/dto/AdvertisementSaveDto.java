package org.ost.platform.advertisement.dto;

public record AdvertisementSaveDto(Long id, String title, String description, java.util.Set<Long> categoryIds) {}
