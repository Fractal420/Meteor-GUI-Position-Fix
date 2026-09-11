/*
 * Meteor GUI Position Fix
 *
 * WContainer walks its cells list front-to-back for both rendering and
 * mouse clicks. Later children are therefore drawn on top, but earlier
 * children receive the click first — so when two category windows
 * overlap (or a category sits under the top tab bar's sibling widgets),
 * the one underneath steals the click and you can't drag or collapse
 * the one you can see.
 *
 * These redirects make mouseClicked / mouseReleased walk the same list
 * back-to-front, matching paint order: the topmost window gets the
 * event. mouseMoved is left alone (it already fans out to every child
 * and the active drag flag lives on the window itself).
 */
package com.meteorfix.gui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

@Mixin(WContainer.class)
public abstract class WContainerHitOrderMixin {

    @Redirect(
        method = "mouseClicked",
        at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<?> meteorGuiPositionFix$reverseClickOrder(List<?> list) {
        return reverse(list);
    }

    @Redirect(
        method = "mouseReleased",
        at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<?> meteorGuiPositionFix$reverseReleaseOrder(List<?> list) {
        return reverse(list);
    }

    private static Iterator<?> reverse(List<?> list) {
        if (list == null || list.isEmpty()) {
            return java.util.Collections.emptyIterator();
        }
        ListIterator<?> it = list.listIterator(list.size());
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasPrevious();
            }

            @Override
            public Object next() {
                return it.previous();
            }
        };
    }
}
