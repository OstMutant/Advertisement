package org.ost.advertisement.ui.views.services;

import org.ost.advertisement.i18n.InstantFormatter;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class VaadinInstantFormatter implements InstantFormatter {

    @Override
    public String formatInstantHuman(Instant instant) {
        return TimeZoneUtil.formatInstantHuman(instant);
    }

    @Override
    public String formatInstant(Instant instant) {
        return TimeZoneUtil.formatInstant(instant);
    }
}
