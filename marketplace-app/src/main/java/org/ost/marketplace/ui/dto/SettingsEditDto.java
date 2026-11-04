package org.ost.marketplace.ui.dto;

import lombok.*;

/** Mutable DTO for user settings form binding to Vaadin Binder. */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class SettingsEditDto implements EditDto {

    private Long    id;
    private Integer adsPageSize;
    private Integer usersPageSize;
    private Integer timelinePageSize;
    private Integer providerProfilesPageSize;
    private Integer feedbackPageSize;
    private Integer moderationPageSize;
    private long    version;
}
