package org.ost.advertisement.ui.views.utils;

import com.vaadin.flow.component.Component;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HighlighterUtil {

    private static final String CLEAN = "highlight-clean";
    private static final String DIRTY = "highlight-dirty";
    private static final String CHANGED = "highlight-changed";
    private static final String INVALID = "highlight-invalid";

    public static void setDefaultBorder(Component component) {
        resetHighlightClasses(component);
        component.addClassName(CLEAN);
    }

    public static <T> void highlight(Component component, T newValue, T originalValue, T defaultValue) {
        highlight(component, newValue, originalValue, defaultValue, true);
    }

    public static <T> void highlight(Component component, T newValue, T originalValue, T defaultValue, boolean isValid) {
        resetHighlightClasses(component);

        if (!isValid && SupportUtil.hasChanged(newValue, defaultValue)) {
            component.addClassName(INVALID);
            return;
        }

        if (SupportUtil.hasChanged(newValue, originalValue)) {
            component.addClassName(DIRTY);
            return;
        }
        if (SupportUtil.hasChanged(originalValue, defaultValue)) {
            component.addClassName(CHANGED);
            return;
        }
        component.addClassName(CLEAN);
    }

    public static void setChanged(Component component) {
        resetHighlightClasses(component);
        component.addClassName(CHANGED);
    }

    public static void setDirtyOrInvalid(Component component, boolean isValid) {
        resetHighlightClasses(component);
        component.addClassName(isValid ? DIRTY : INVALID);
    }

    public static void setDirtyOrClean(Component component, boolean dirty) {
        resetHighlightClasses(component);
        component.addClassName(dirty ? DIRTY : CLEAN);
    }

    private static void resetHighlightClasses(Component component) {
        component.removeClassName(CLEAN);
        component.removeClassName(DIRTY);
        component.removeClassName(CHANGED);
        component.removeClassName(INVALID);
    }
}