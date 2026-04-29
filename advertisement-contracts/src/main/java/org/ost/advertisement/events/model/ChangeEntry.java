package org.ost.advertisement.events.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeEntry.FieldChange.class,   name = "field"),
        @JsonSubTypes.Type(value = ChangeEntry.SettingChange.class, name = "setting"),
        @JsonSubTypes.Type(value = ChangeEntry.NoteEntry.class,     name = "note"),
        @JsonSubTypes.Type(value = ChangeEntry.GenericChange.class, name = "generic")
})
public sealed interface ChangeEntry
        permits ChangeEntry.FieldChange, ChangeEntry.SettingChange, ChangeEntry.NoteEntry, ChangeEntry.GenericChange {

    record FieldChange(String field, String from, String to) implements ChangeEntry {}

    record SettingChange(String key, int from, int to) implements ChangeEntry {}

    record NoteEntry(String text) implements ChangeEntry {}

    record GenericChange(String labelI18nKey, String before, String after) implements ChangeEntry {}
}
