package org.ost.advertisement.ui.views.components.query.action.elements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.ui.views.components.SvgIcon;

public class QueryActionButton extends Button {

	public QueryActionButton(String svgPath, String tooltip, ButtonVariant variant) {
		this.setText("");
		this.getStyle().set("border", "3px solid transparent");
		this.getStyle().set("border-radius", "4px");
		this.setIcon(new SvgIcon("icons/" + svgPath));
		this.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		this.getElement().setProperty("title", tooltip);
	}

	public void updateColor(DirtyHighlightColor color) {
		this.getStyle().set("border-color", color.getCssColor());
	}

	public void setDirty(boolean dirty) {
		updateColor(dirty ? DirtyHighlightColor.DIRTY : DirtyHighlightColor.CLEAN);
	}

	@AllArgsConstructor
	@Getter
	public enum DirtyHighlightColor {
		CLEAN("transparent"),
		DIRTY("orange");

		private final String cssColor;
	}
}
