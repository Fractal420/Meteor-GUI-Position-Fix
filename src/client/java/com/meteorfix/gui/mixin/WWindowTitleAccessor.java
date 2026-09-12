package com.meteorfix.gui.mixin;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WWindow.class)
public interface WWindowTitleAccessor {
    @Accessor("title")
    String meteorGuiPositionFix$getTitle();
}
