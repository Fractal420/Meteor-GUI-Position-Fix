/*
 * Meteor GUI Position Fix
 *
 * `move(double, double)` is declared directly on WContainer (WWindow does
 * not override it), and it's exactly what runs when the user drags a
 * window's header - so this is the one universal place to observe "the
 * user just moved this window" for literally any WWindow anywhere in the
 * client, vanilla Meteor or any addon's, without needing to know anything
 * about how that addon built it.
 *
 * Because WContainer.move() is also called recursively for every nested
 * child container as part of moving a window's contents along with it,
 * this only acts when the receiver is actually a WWindow - everything
 * else is ignored.
 *
 * WindowPositionMemory.afterMove() both clamps the window so a drag can
 * never push it past the screen edge, and immediately saves the result
 * as that window's new pinned position. The clamp itself is applied as a
 * corrective move(); a re-entrancy guard prevents this mixin from
 * calling afterMove again during that correction (which would otherwise
 * unstick children from the window).
 */
package com.meteorfix.gui.mixin;

import com.meteorfix.gui.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WContainer.class)
public abstract class WContainerMoveMixin {

    @Inject(method = "move", at = @At("TAIL"), require = 0)
    private void meteorGuiPositionFix$onMove(double deltaX, double deltaY, CallbackInfo ci) {
        try {
            if (WindowPositionMemory.isClamping()) return;
            if ((Object) this instanceof WWindow window) {
                WindowPositionMemory.afterMove(window);
            }
        } catch (Throwable ignored) {
            // A tracking hiccup should never affect actual dragging.
        }
    }
}
