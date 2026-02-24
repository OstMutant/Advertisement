package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import org.ost.advertisement.ui.views.advertisements.overlay.OverlaySession;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;

// Each handler owns its header buttons and body content for a specific mode.
// configure() must be called before init().
// deactivate() must fully hide everything the handler has shown.
public interface ModeHandler {
    void configure(OverlayLayout layout, Runnable primary, Runnable secondary);
    void init();
    void activate(OverlaySession session);
    void deactivate();
}