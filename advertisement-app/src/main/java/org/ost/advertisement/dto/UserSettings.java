package org.ost.advertisement.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = UserSettings.UserSettingsBuilder.class)
public class UserSettings {

    @Min(5) @Max(100)
    int adsPageSize;

    @Min(5) @Max(100)
    int usersPageSize;

    public static UserSettings defaultSettings() {
        return UserSettings.builder()
                .adsPageSize(20)
                .usersPageSize(20)
                .build();
    }

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class UserSettingsBuilder {}
}
