package org.ost.marketplace.ui.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class CityEditDto implements EditDto {

    private Long   id;
    private String nameEn;
    private String descriptionEn;
    private String nameUk;
    private String descriptionUk;
}
