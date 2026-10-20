package org.ost.platform.taxon.model;

/** Thrown when a caller restores a taxon that is not soft-deleted, so HTTP mapping can tell it apart from an unexpected-state bug. */
public class TaxonNotDeletedException extends RuntimeException {

    public TaxonNotDeletedException(String message) {
        super(message);
    }
}
