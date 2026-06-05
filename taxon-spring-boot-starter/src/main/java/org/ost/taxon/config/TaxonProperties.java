package org.ost.taxon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "taxon")
public record TaxonProperties(
        @DefaultValue("en")       Locale       defaultLocale,
        @DefaultValue({"uk", "en"}) List<Locale> supportedLocales
) {}
