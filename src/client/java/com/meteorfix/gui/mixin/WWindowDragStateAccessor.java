package com.meteorfix.gui.mixin;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WWindow.class)
public interface WWindowDragStateAccessor {
    @Accessor("moved")
    boolean meteorGuiPositionFix$getMoved();
    @Accessor("moved")
    void meteorGuiPositionFix$setMoved(boolean value);
    @Accessor("movedX")
    void meteorGuiPositionFix$setMovedX(double value);
    @Accessor("movedY")
    void meteorGuiPositionFix$setMovedY(double value);
}
