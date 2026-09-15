package org.ost.orchestrator.services;

import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Splits one entity's assigned-taxon list into category ids/names and its first assigned city, shared by every domain's own display-enrichment step. */
record CategoryAndCitySplit(Set<Long> categoryIds, List<String> categoryNames, Long cityTaxonId, String cityName) {

    static CategoryAndCitySplit of(List<TaxonDto> assigned) {
        Set<Long> catIds = new LinkedHashSet<>();
        List<String> catNames = new ArrayList<>();
        TaxonDto city = null;
        for (TaxonDto t : assigned) {
            if (t.getType() == TaxonType.CATEGORY) {
                catIds.add(t.getId());
                catNames.add(t.getName());
            } else if (t.getType() == TaxonType.CITY && city == null) {
                city = t;
            }
        }
        return new CategoryAndCitySplit(catIds, catNames, city != null ? city.getId() : null, city != null ? city.getName() : null);
    }
}
