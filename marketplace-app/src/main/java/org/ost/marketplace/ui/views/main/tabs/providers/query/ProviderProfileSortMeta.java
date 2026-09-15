package org.ost.marketplace.ui.views.main.tabs.providers.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Typed {@link org.ost.marketplace.ui.query.sort.SortFieldMeta} constants for the Providers catalog, referencing {@code ProviderProfileDto.Fields.*}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderProfileSortMeta {

    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of(ProviderProfileDto.Fields.createdAt, PROVIDERS_SORT_CREATED_AT);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of(ProviderProfileDto.Fields.updatedAt, PROVIDERS_SORT_UPDATED_AT);
}
