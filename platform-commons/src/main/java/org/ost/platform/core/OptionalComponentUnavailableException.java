package org.ost.platform.core;

import lombok.Getter;
import lombok.NonNull;

/** Thrown by {@link ComponentFactory#get()} when the optional component it stands for is not installed; carries the missing type. */
@Getter
public class OptionalComponentUnavailableException extends RuntimeException {

    /** The port or hook that has no implementation in this application. */
    private final Class<?> componentType;

    public OptionalComponentUnavailableException(@NonNull Class<?> componentType, @NonNull Throwable cause) {
        super("No " + componentType.getSimpleName() + " is available: the module that provides it is not installed", cause);
        this.componentType = componentType;
    }
}
