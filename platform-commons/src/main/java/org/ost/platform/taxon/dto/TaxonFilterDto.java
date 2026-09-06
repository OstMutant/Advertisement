package org.ost.platform.taxon.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/** Query-time search filter for taxon catalog listings — the same shape reused for REST query-param binding. */
@FieldNameConstants
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class TaxonFilterDto {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    public static TaxonFilterDto empty() {
        return new TaxonFilterDto();
    }
}
