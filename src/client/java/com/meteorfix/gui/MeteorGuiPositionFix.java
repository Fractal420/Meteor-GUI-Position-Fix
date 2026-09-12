package com.meteorfix.gui;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeteorGuiPositionFix extends MeteorAddon {
    private static final Logger LOGGER = LoggerFactory.getLogger("meteor-gui-position-fix");

    @Override
    public void onInitialize() {
        LOGGER.info("[MeteorGuiPositionFix] Addon loaded.");
    }

    @Override
    public String getPackage() {
        return "com.meteorfix.gui";
    }
}
