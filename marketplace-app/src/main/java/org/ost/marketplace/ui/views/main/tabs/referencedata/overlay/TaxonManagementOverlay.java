package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay;

import lombok.NonNull;
import org.ost.platform.taxon.dto.TaxonDto;

import java.util.function.Consumer;

/** Contract shared by {@link CityOverlay}/{@link CategoryOverlay} so {@code AbstractTaxonManagementView} can open either without depending on the concrete class. */
public interface TaxonManagementOverlay {

    void openForView(@NonNull TaxonDto entity, @NonNull Consumer<TaxonDto> onUpdated);

    void openForCreate(@NonNull Runnable onListChanged);

    void openForEdit(@NonNull TaxonDto entity, @NonNull Consumer<TaxonDto> onUpdated);
}
