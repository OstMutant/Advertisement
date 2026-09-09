package org.ost.taxon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;

/** Binds the {@code taxon.*} configuration prefix: the fallback locale used when a translation is missing, and the locales a taxon's translations must cover. */
@ConfigurationProperties(prefix = "taxon")
public record TaxonProperties(
        @DefaultValue("en")         Locale       defaultLocale,
        @DefaultValue({"uk", "en"}) List<Locale> supportedLocales
) {}
