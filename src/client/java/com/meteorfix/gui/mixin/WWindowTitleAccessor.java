/*
 * Meteor GUI Position Fix
 *
 * `title` is `protected final String` directly on WWindow itself (not
 * inherited from an ancestor), so shadowing it would normally be perfectly
 * safe - but we need to read it from a plain utility class outside the
 * mixin package too, and protected fields aren't visible there. An
 * @Accessor mixin generates a real, safe, public getter on the actual
 * runtime WWindow class, which any code can call. This is the standard,
 * lowest-risk way to expose a single field like this.
 */
package com.meteorfix.gui.mixin;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WWindow.class)
public interface WWindowTitleAccessor {
    @Accessor("title")
    String meteorGuiPositionFix$getTitle();
}
