package org.ost.platform.providerprofile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.Set;

@FieldNameConstants
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class ProviderProfileFilterDto {

    private Set<ProviderKind> kinds;
    private Set<Long> categoryIds;
    private Long cityTaxonId;

    public static ProviderProfileFilterDto empty() {
        return new ProviderProfileFilterDto();
    }
}
