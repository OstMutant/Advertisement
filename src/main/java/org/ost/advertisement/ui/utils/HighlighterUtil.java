package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.Component;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

public class HighlighterUtil {

    public static void setDefaultBorder(Component component) {
        component.getStyle().set("border", "3px solid transparent")
                .set("border-radius", "4px")
                .set("padding", "0px");
    }

    public static <T> void highlight(Component component, T newValue, T originalValue, T defaultValue) {
        highlight(component, newValue, originalValue, defaultValue, true);
    }

    public static <T> void highlight(Component component, T newValue, T originalValue, T defaultValue,
                                     boolean isValid) {
        if (hasChanged(newValue, originalValue)) {
            setDirtyOrInvalid(component, isValid);
            return;
        }
        if (hasChanged(originalValue, defaultValue)) {
            setChanged(component);
            return;
        }
        setClean(component);
    }

    public static void setClean(Component component) {
        setBorderColor(component, DirtyHighlightColor.CLEAN);
    }

    public static void setChanged(Component component) {
        setBorderColor(component, DirtyHighlightColor.CHANGED);
    }

    public static void setDirtyOrInvalid(Component component, boolean isValid) {
        setBorderColor(component, isValid ? DirtyHighlightColor.DIRTY : DirtyHighlightColor.INVALID);
    }

    public static void setDirtyOrClean(Component component, boolean dirty) {
        setBorderColor(component, dirty ? DirtyHighlightColor.DIRTY : DirtyHighlightColor.CLEAN);
    }

    private static void setBorderColor(Component component, DirtyHighlightColor color) {
        component.getStyle().set("border-color", color.getCssColor());
    }

    @AllArgsConstructor
    @Getter
    public enum DirtyHighlightColor {
        CLEAN("transparent"),
        DIRTY("orange"),
        CHANGED("blue"),
        INVALID("red");

        private final String cssColor;
    }
}
