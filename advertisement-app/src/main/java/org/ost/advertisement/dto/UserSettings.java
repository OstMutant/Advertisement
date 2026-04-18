package org.ost.advertisement.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;
import org.ost.advertisement.common.PaginationDefaults;

@Value
@Builder
@JsonDeserialize(builder = UserSettings.UserSettingsBuilder.class)
public class UserSettings {

    @Min(PaginationDefaults.MIN_PAGE_SIZE) @Max(PaginationDefaults.MAX_PAGE_SIZE)
    int adsPageSize;

    @Min(PaginationDefaults.MIN_PAGE_SIZE) @Max(PaginationDefaults.MAX_PAGE_SIZE)
    int usersPageSize;

    public static UserSettings defaultSettings() {
        return UserSettings.builder()
                .adsPageSize(PaginationDefaults.DEFAULT_PAGE_SIZE)
                .usersPageSize(PaginationDefaults.DEFAULT_PAGE_SIZE)
                .build();
    }

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class UserSettingsBuilder {}
}
