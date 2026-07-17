package org.ost.platform.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.function.UnaryOperator;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeEntry.FieldChange.class, name = "field"),
        @JsonSubTypes.Type(value = ChangeEntry.MediaChange.class, name = "media")
})
public sealed interface ChangeEntry
        permits ChangeEntry.FieldChange, ChangeEntry.MediaChange {

    record FieldChange(String field, String from, String to) implements ChangeEntry {}

    record MediaChange(String before, String after) implements ChangeEntry {}

    /**
     * Applies {@code fromFn}/{@code toFn} to this entry's values if it is a {@link FieldChange}
     * for {@code fieldName}; returns this entry unchanged otherwise (including for any
     * {@link MediaChange}). The single instanceof-check on {@link FieldChange} in the codebase —
     * callers that need to conditionally transform one specific field's value call this instead
     * of writing their own type check.
     */
    default ChangeEntry replaceIfField(String fieldName, UnaryOperator<String> fromFn, UnaryOperator<String> toFn) {
        if (this instanceof FieldChange(var field, var from, var to) && fieldName.equals(field)) {
            return new FieldChange(field, fromFn.apply(from), toFn.apply(to));
        }
        return this;
    }
}
