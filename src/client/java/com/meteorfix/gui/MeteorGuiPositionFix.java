/*
 * Meteor GUI Position Fix
 * Minecraft 1.21.11 / Meteor 1.21.11-82
 */
package com.meteorfix.gui;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeteorGuiPositionFix extends MeteorAddon {
    private static final Logger LOGGER = LoggerFactory.getLogger("meteor-gui-position-fix");

    @Override
    public void onInitialize() {
        // The fix itself is implemented by the mixins; this log line is
        // only here so it's easy to confirm from latest.log that the
        // addon actually loaded. Look for "MeteorGuiPositionFix" lines -
        // if you never see the "hook is active" lines logged by
        // WindowPositionMemory after opening the ClickGUI, the mixins
        // aren't being applied on your setup.
        LOGGER.info("[MeteorGuiPositionFix] Addon loaded.");
    }

    @Override
    public String getPackage() {
        return "com.meteorfix.gui";
    }
}
