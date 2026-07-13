package org.ost.taxon.repository;

import org.ost.taxon.entities.Taxon;
import org.springframework.data.repository.CrudRepository;

public interface TaxonCrudRepository extends CrudRepository<Taxon, Long> {
}
