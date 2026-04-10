package org.ost.advertisement.configuration.ui;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

@Theme("my-app")
@PWA(name = "Advertisement App", shortName = "AdvertApp")
@JsModule("@vaadin/vaadin-lumo-styles/vaadin-iconset.js")
public class AppShell implements AppShellConfigurator {
}
