package org.ost.advertisement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeEntry.FieldChange.class,   name = "field"),
        @JsonSubTypes.Type(value = ChangeEntry.PhotoChange.class,   name = "photos"),
        @JsonSubTypes.Type(value = ChangeEntry.SettingChange.class, name = "setting"),
        @JsonSubTypes.Type(value = ChangeEntry.NoteEntry.class,     name = "note")
})
public sealed interface ChangeEntry
        permits ChangeEntry.FieldChange, ChangeEntry.PhotoChange,
                ChangeEntry.SettingChange, ChangeEntry.NoteEntry {

    @JsonIgnore
    default boolean isPhoto() { return false; }

    /** A text field that was created (from=null) or updated. */
    record FieldChange(String field, String from, String to) implements ChangeEntry {}

    /** Photo list before and after the change. */
    record PhotoChange(List<String> before, List<String> after) implements ChangeEntry {
        @Override public boolean isPhoto() { return true; }
    }

    /** A numeric settings value that changed. */
    record SettingChange(String key, int from, int to) implements ChangeEntry {}

    /** Free-form note appended to a snapshot. */
    record NoteEntry(String text) implements ChangeEntry {}
}
