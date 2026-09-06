package org.ost.marketplace.ui.dto;

import lombok.*;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
/** Mutable form-binding DTO for the Provider Profile edit form -- Vaadin's {@code Binder}
 *  needs setters. */
public class ProviderProfileEditDto implements EditDto {

    private Long id;

    private ProviderKind kind;
    private String       about;

    private Set<Long> categoryIds;
    private Long      cityTaxonId;

    private Long version;
}
