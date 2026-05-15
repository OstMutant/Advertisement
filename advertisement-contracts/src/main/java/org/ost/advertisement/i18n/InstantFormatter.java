package org.ost.advertisement.i18n;

import java.time.Instant;

public interface InstantFormatter {
    String formatInstantHuman(Instant instant);
    String formatInstant(Instant instant);
}
