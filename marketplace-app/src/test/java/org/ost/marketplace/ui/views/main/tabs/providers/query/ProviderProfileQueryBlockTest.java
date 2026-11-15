package org.ost.marketplace.ui.views.main.tabs.providers.query;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pins that the ProviderProfileQueryBlock shows its category and city filter rows only when the taxon catalog is available. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileQueryBlockTest {

    @Mock private FilterProcessor<ProviderProfileFilterDto> filterProcessor;
    @Mock private SortProcessor sortProcessor;
    @Mock private I18nService i18nService;
    @Mock private LocaleProvider localeProvider;
    @Mock private TaxonCatalogService taxonCatalogService;

    private ProviderProfileQueryBlock block;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.get(any(I18nKey.class))).thenReturn("text");
        lenient().when(localeProvider.getCurrentLocale()).thenReturn(Locale.ENGLISH);
        block = new ProviderProfileQueryBlock(filterProcessor, sortProcessor, i18nService, localeProvider, taxonCatalogService);
    }

    @Test
    void initLayout_taxonCatalogUnavailable_buildsNeitherCategoryNorCityField() {
        when(taxonCatalogService.isAvailable()).thenReturn(false);

        ReflectionTestUtils.invokeMethod(block, "initLayout");

        assertThat(descendants(block).filter(MultiSelectComboBox.class::isInstance)
                .map(c -> c.getElement().getAttribute("data-testid")))
                .doesNotContain("provider-profile-filter-categories");
        assertThat(descendants(block).filter(ComboBox.class::isInstance)).isEmpty();
    }

    @Test
    void initLayout_taxonCatalogAvailable_buildsCategoryAndCityFieldsFromTheCatalog() {
        when(taxonCatalogService.isAvailable()).thenReturn(true);
        when(taxonCatalogService.getAllByType(any(), any())).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(block, "initLayout");

        assertThat(descendants(block).filter(MultiSelectComboBox.class::isInstance)
                .map(c -> c.getElement().getAttribute("data-testid")))
                .contains("provider-profile-filter-categories");
        assertThat(descendants(block).filter(ComboBox.class::isInstance)).hasSize(1);
        verify(taxonCatalogService).getAllByType(TaxonType.CATEGORY, Locale.ENGLISH);
        verify(taxonCatalogService).getAllByType(TaxonType.CITY, Locale.ENGLISH);
    }

    private static Stream<Component> descendants(Component root) {
        return root.getChildren().flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
