package org.ost.marketplace.ui.dto;

import lombok.*;

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
    private long    version;
}
