/*
 * Meteor GUI Position Fix
 *
 * Meteor's own onCalculateWidgetPositions restores a saved WindowConfig
 * position and then clamps it with:
 *   if (y + height > screenHeight) y = screenHeight - height;
 *
 * Because WWindow.height is always the fully-expanded size, a collapsed
 * category that was previously placed near the bottom gets shoved back
 * toward the top on every reopen — even before our afterLayout restore
 * runs. This mixin redirects the two getfield height reads that feed that
 * clamp so they return the visible (collapsed-aware) height instead.
 *
 * The redirect is deliberately narrow (only the height field on WWindow
 * inside this method) and marked require=0 so a future Meteor change just
 * disables the redirect instead of breaking the GUI.
 */
package com.meteorfix.gui.mixin;

import com.meteorfix.gui.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WWindow.class)
public abstract class WWindowClampMixin {

    /**
     * Replace the raw `this.height` reads used by Meteor's on-screen
     * clamp inside onCalculateWidgetPositions with the visible height.
     * The two occurrences (the comparison and the subtraction) both go
     * through the same getfield, so one redirect covers both.
     */
    @Redirect(
        method = "onCalculateWidgetPositions",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WWindow;height:D",
            opcode = 180 /* GETFIELD */
        ),
        require = 0
    )
    private double meteorGuiPositionFix$useVisibleHeightForClamp(WWindow window) {
        try {
            return WindowPositionMemory.effectiveHeight(window);
        } catch (Throwable t) {
            return window.height;
        }
    }
}
