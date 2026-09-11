/*
 * Meteor GUI Position Fix
 *
 * WWindow's nested WHeader is the widget that actually receives the
 * mouse-up that ends a category drag. After the vanilla handler clears
 * the `dragging` flag we resolve overlaps for the outer WWindow so the
 * released category snaps to a non-overlapping position.
 *
 * Targeting the nested class by binary name is the standard Mixin approach
 * for protected inner types that are not public API.
 */
package com.meteorfix.gui.mixin;

import com.meteorfix.gui.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "meteordevelopment.meteorclient.gui.widgets.containers.WWindow$WHeader")
public abstract class WHeaderReleaseMixin {

    private static final Field OUTER;
    private static final Field DRAGGED;

    static {
        Field outer = null;
        Field dragged = null;
        try {
            Class<?> headerClass = Class.forName(
                "meteordevelopment.meteorclient.gui.widgets.containers.WWindow$WHeader");
            outer = headerClass.getDeclaredField("this$0");
            outer.setAccessible(true);
            dragged = WWindow.class.getDeclaredField("dragged");
            dragged.setAccessible(true);
        } catch (Throwable t) {
            // If reflection fails the release hook simply becomes a no-op.
        }
        OUTER = outer;
        DRAGGED = dragged;
    }

    @Inject(method = "onMouseReleased", at = @At("TAIL"), require = 0)
    private void meteorGuiPositionFix$onRelease(MouseButtonEvent click,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            if (OUTER == null || DRAGGED == null) return;

            Object outer = OUTER.get(this);
            if (!(outer instanceof WWindow window)) return;

            // After the vanilla body has run, `dragging` is already false.
            // `dragged` is still true if the user actually moved the window
            // (as opposed to a simple click that toggles expanded).
            if (!DRAGGED.getBoolean(window)) return;

            WindowPositionMemory.afterDragEnd(window);
        } catch (Throwable ignored) {
            // A resolve hiccup must never break the ClickGUI.
        }
    }
}
