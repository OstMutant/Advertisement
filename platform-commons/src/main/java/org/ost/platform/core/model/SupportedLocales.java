package org.ost.platform.core.model;

import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Locale;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

/** The interface languages the platform supports and the one used when none applies; the single source for every adapter and service. */
@NoArgsConstructor(access = PRIVATE)
public final class SupportedLocales {

    public static final String ENGLISH_TAG = "en";
    public static final String UKRAINIAN_TAG = "uk";
    public static final String DEFAULT_TAG = ENGLISH_TAG;
    /** Regular expression accepting exactly the supported language tags, usable in a validation annotation. */
    public static final String TAG_PATTERN = ENGLISH_TAG + "|" + UKRAINIAN_TAG;
    public static final Locale DEFAULT = Locale.forLanguageTag(DEFAULT_TAG);

    private static final Set<String> TAGS = Set.of(ENGLISH_TAG, UKRAINIAN_TAG);

    public static boolean isSupported(@NonNull Locale locale) {
        return TAGS.contains(locale.getLanguage());
    }
}
