package org.ost.platform.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants
@JsonDeserialize(builder = UserSettingsDto.UserSettingsDtoBuilder.class)
public class UserSettingsDto {

    @Min(5) @Max(100)
    int adsPageSize;

    @Min(5) @Max(100)
    int usersPageSize;

    public static UserSettingsDto defaultSettings() {
        return UserSettingsDto.builder()
                .adsPageSize(20)
                .usersPageSize(20)
                .build();
    }

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class UserSettingsDtoBuilder {}
}
