package org.ost.platform.providerprofile.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Value
@Builder(toBuilder = true)
@FieldNameConstants
public class ProviderProfileDto {

    Long id;
    Long actorId;
    String actorName;
    String actorEmail;
    ProviderKind kind;
    String about;
    Long cityTaxonId;
    String cityName;
    Set<Long> categoryIds;
    List<String> categoryNames;
    Instant createdAt;
    Instant updatedAt;
    Long version;
}
