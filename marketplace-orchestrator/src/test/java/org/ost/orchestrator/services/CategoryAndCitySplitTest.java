package org.ost.orchestrator.services;

import org.junit.jupiter.api.Test;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link CategoryAndCitySplit#of} -- shared category/city split reused by every domain's own display-enrichment service. */
class CategoryAndCitySplitTest {

    private static TaxonDto taxon(long id, TaxonType type, String name) {
        return TaxonDto.builder().id(id).type(type).name(name).description("").build();
    }

    @Test
    void of_noAssignedTaxons_returnsEmptyCategoriesAndNullCity() {
        CategoryAndCitySplit split = CategoryAndCitySplit.of(List.of());

        assertThat(split.categoryIds()).isEmpty();
        assertThat(split.categoryNames()).isEmpty();
        assertThat(split.cityTaxonId()).isNull();
        assertThat(split.cityName()).isNull();
    }

    @Test
    void of_categoriesAndOneCity_splitsBothCorrectly() {
        List<TaxonDto> assigned = List.of(
                taxon(1L, TaxonType.CATEGORY, "Plumbing"),
                taxon(2L, TaxonType.CATEGORY, "Electrics"),
                taxon(7L, TaxonType.CITY, "Lviv"));

        CategoryAndCitySplit split = CategoryAndCitySplit.of(assigned);

        assertThat(split.categoryIds()).containsExactly(1L, 2L);
        assertThat(split.categoryNames()).containsExactly("Plumbing", "Electrics");
        assertThat(split.cityTaxonId()).isEqualTo(7L);
        assertThat(split.cityName()).isEqualTo("Lviv");
    }

    @Test
    void of_multipleCitiesAssigned_keepsOnlyTheFirstOne() {
        List<TaxonDto> assigned = List.of(
                taxon(7L, TaxonType.CITY, "Lviv"),
                taxon(8L, TaxonType.CITY, "Kyiv"));

        CategoryAndCitySplit split = CategoryAndCitySplit.of(assigned);

        assertThat(split.cityTaxonId()).isEqualTo(7L);
        assertThat(split.cityName()).isEqualTo("Lviv");
    }
}
