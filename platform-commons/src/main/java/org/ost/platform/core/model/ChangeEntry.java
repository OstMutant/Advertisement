package org.ost.platform.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeEntry.FieldChange.class, name = "field"),
        @JsonSubTypes.Type(value = ChangeEntry.MediaChange.class, name = "media")
})
public sealed interface ChangeEntry
        permits ChangeEntry.FieldChange, ChangeEntry.MediaChange {

    record FieldChange(String field, String from, String to) implements ChangeEntry {}

    record MediaChange(String before, String after) implements ChangeEntry {}
}
