package org.ost.advertisement.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeEntry.FieldChange.class,   name = "field"),
        @JsonSubTypes.Type(value = ChangeEntry.SettingChange.class, name = "setting"),
        @JsonSubTypes.Type(value = ChangeEntry.NoteEntry.class,     name = "note"),
        @JsonSubTypes.Type(value = ChangeEntry.PhotoChange.class,   name = "photo")
})
public sealed interface ChangeEntry
        permits ChangeEntry.FieldChange, ChangeEntry.SettingChange, ChangeEntry.NoteEntry, ChangeEntry.PhotoChange {

    /** A text field that was created (from=null) or updated. */
    record FieldChange(String field, String from, String to) implements ChangeEntry {}

    /** A numeric settings value that changed. */
    record SettingChange(String key, int from, int to) implements ChangeEntry {}

    /** Free-form note appended to a snapshot. */
    record NoteEntry(String text) implements ChangeEntry {}

    /** Photo filenames added/removed. */
    record PhotoChange(java.util.List<String> before, java.util.List<String> after) implements ChangeEntry {}
}
