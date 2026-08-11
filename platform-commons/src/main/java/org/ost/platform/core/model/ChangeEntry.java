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

    /** Replaces {@code fieldName}'s value via {@code fromFn}/{@code toFn}; passes through unchanged otherwise. */
    default ChangeEntry replaceIfField(String fieldName, UnaryOperator<String> fromFn, UnaryOperator<String> toFn) {
        if (this instanceof FieldChange(var field, var from, var to) && fieldName.equals(field)) {
            return new FieldChange(field, fromFn.apply(from), toFn.apply(to));
        }
        return this;
    }

    /** Maps this entry's field name via {@code fieldFn} if it's a {@link FieldChange}; passes through unchanged otherwise. */
    default ChangeEntry mapField(UnaryOperator<String> fieldFn) {
        if (this instanceof FieldChange(var field, var from, var to)) {
            return new FieldChange(fieldFn.apply(field), from, to);
        }
        return this;
    }
}
