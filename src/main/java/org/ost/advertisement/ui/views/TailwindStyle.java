package org.ost.advertisement.ui.views;

import com.vaadin.flow.component.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum TailwindStyle {
	BASE_LABEL(null, "px-2", "py-1"),
	GRAY_LABEL(BASE_LABEL, "text-sm", "text-gray-500"),
	EMAIL_LABEL(BASE_LABEL, "text-base", "font-bold", "text-blue-600"),
	SCROLL_CONTAINER(null, "h-full", "overflow-auto", "flex-grow");

	private final List<String> classes = new ArrayList<>();

	TailwindStyle(TailwindStyle parent, String... classes) {
		List<String> combined = new ArrayList<>(Arrays.asList(classes));
		if (parent != null) {
			for (String cls : parent.classes) {
				if (!combined.contains(cls)) {
					combined.add(cls);
				}
			}
		}
		this.classes.addAll(combined);
	}

	public void apply(Component component) {
		component.addClassNames(classes.toArray(new String[0]));
	}

	public static void applyAll(Component component, TailwindStyle... styles) {
		for (TailwindStyle style : styles) {
			style.apply(component);
		}
	}
}
