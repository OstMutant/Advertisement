package org.ost.platform.providerprofile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.core.validation.ValidRange;

import java.time.Instant;
import java.util.Set;

@FieldNameConstants
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ValidRange(start = "createdAtStart", end = "createdAtEnd", message = "createdAtStart must not be after createdAtEnd")
@ValidRange(start = "updatedAtStart", end = "updatedAtEnd", message = "updatedAtStart must not be after updatedAtEnd")
public class ProviderProfileFilterDto {

    private Set<ProviderKind> kinds;

    private Instant createdAtStart;
    private Instant createdAtEnd;
    private Instant updatedAtStart;
    private Instant updatedAtEnd;

    private Set<Long> categoryIds;
    private Long cityTaxonId;

    public static ProviderProfileFilterDto empty() {
        return new ProviderProfileFilterDto();
    }
}
