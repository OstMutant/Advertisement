package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import org.ost.advertisement.ui.views.advertisements.overlay.OverlaySession;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;

public interface ModeHandler {
    void setCallbacks(Runnable primary, Runnable secondary);
    void activate(OverlaySession session, OverlayLayout layout);
    void deactivate();
}