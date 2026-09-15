package org.ost.taxon.repository;

import org.ost.taxon.entities.Taxon;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find CRUD for {@code taxon}, delegated to by {@link TaxonRepository}. */
public interface TaxonCrudRepository extends CrudRepository<Taxon, Long> {
}
