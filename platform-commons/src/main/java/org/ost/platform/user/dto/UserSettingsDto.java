package org.ost.platform.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder(toBuilder = true)
@FieldNameConstants
@JsonDeserialize(builder = UserSettingsDto.UserSettingsDtoBuilder.class)
public class UserSettingsDto {

    @Min(5) @Max(100)
    int adsPageSize;

    @Min(5) @Max(100)
    int usersPageSize;

    @Min(5) @Max(100)
    @Builder.Default
    int timelinePageSize = 20;

    long version;

    public static UserSettingsDto defaultSettings() {
        return UserSettingsDto.builder()
                .adsPageSize(20)
                .usersPageSize(20)
                .timelinePageSize(20)
                .version(0)
                .build();
    }

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class UserSettingsDtoBuilder {}
}
