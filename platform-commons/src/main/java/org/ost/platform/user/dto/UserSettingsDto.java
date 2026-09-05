package org.ost.platform.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.user.model.PageSizeLimits;
import tools.jackson.databind.annotation.JsonDeserialize;

@Value
@Builder(toBuilder = true)
@FieldNameConstants
@JsonDeserialize(builder = UserSettingsDto.UserSettingsDtoBuilder.class)
public class UserSettingsDto {

    public static final int SCHEMA_VERSION = 1;

    @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE)
    int adsPageSize;

    @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE)
    int usersPageSize;

    @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE)
    @Builder.Default
    int timelinePageSize = PageSizeLimits.DEFAULT_PAGE_SIZE;

    long version;

    @Builder.Default
    int schemaVersion = SCHEMA_VERSION;

    public static UserSettingsDto defaultSettings() {
        return UserSettingsDto.builder()
                .adsPageSize(PageSizeLimits.DEFAULT_PAGE_SIZE)
                .usersPageSize(PageSizeLimits.DEFAULT_PAGE_SIZE)
                .timelinePageSize(PageSizeLimits.DEFAULT_PAGE_SIZE)
                .version(0)
                .build();
    }

    @tools.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class UserSettingsDtoBuilder {}
}
