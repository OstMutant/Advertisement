package org.ost.restapi.api.patch;

import lombok.Getter;
import lombok.NonNull;

import java.util.Map;

/** Thrown when a merge-patched record violates its bean-validation constraints; carries property path to first message. */
@Getter
public class PatchValidationException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public PatchValidationException(@NonNull Map<String, String> fieldErrors) {
        super("Patched resource is invalid: " + fieldErrors.keySet());
        this.fieldErrors = fieldErrors;
    }
}
