package org.ost.marketplace.ui.dto;

import lombok.*;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ProviderProfileEditDto implements EditDto {

    private Long id;

    private ProviderKind kind;
    private String       about;

    private Set<Long> categoryIds;
    private Long      cityTaxonId;

    private Long version;
}
