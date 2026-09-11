/*
 * Meteor GUI Position Fix
 *
 * `onCalculateWidgetPositions` is declared directly on WWindow itself.
 * Injecting at RETURN (the tail of the method) means our restore runs
 * after vanilla's own id-based restore, after children have been laid
 * out, and after the "moved" catch-up block - i.e. after everything else
 * that could possibly touch this window's x/y during this layout pass.
 * That makes it the final word for the frame, regardless of what the
 * parent container (vanilla WCategoryController, a custom "Category
 * Manager" container, or anything else) did earlier in the same pass.
 *
 * This applies to every WWindow anywhere in the client, so it fixes the
 * "categories jump around on reopen" bug generically, without depending
 * on any specific addon's container classes.
 *
 * WindowPositionMemory.afterLayout() no longer requires the window to
 * have ever been dragged: the first time a window completes a layout
 * pass, its position is auto-pinned, and every later reopen restores
 * exactly that spot. It's also clamped to stay fully on screen.
 */
package com.meteorfix.gui.mixin;

import com.meteorfix.gui.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WWindow.class)
public abstract class WWindowLayoutMixin {

    @Inject(method = "onCalculateWidgetPositions", at = @At("RETURN"), require = 0)
    private void meteorGuiPositionFix$restorePosition(CallbackInfo ci) {
        try {
            WindowPositionMemory.afterLayout((WWindow) (Object) this);
        } catch (Throwable ignored) {
            // A restore hiccup should fall back to whatever layout the
            // container already computed - never block the menu.
        }
    }
}
