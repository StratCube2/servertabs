package com.servertabs.mixin;

import com.servertabs.gui.ServerTabsSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    // The super constructor is never actually called at runtime —
    // Mixin patches the bytecode of TitleScreen directly.
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /**
     * Injected at the very end of TitleScreen.init() so all vanilla
     * buttons already exist. We place our gear button directly to the
     * right of the Multiplayer button row.
     *
     * Vanilla Multiplayer button position:
     *   x = this.width / 2 - 100   (left edge of the 200px button)
     *   y = this.height / 4 + 48 + 24
     *
     * Our button starts 2px after the right edge (width/2 + 100 + 2 = width/2 + 102).
     * It is 20 x 20 px so it stays compact and never overlaps the vanilla layout.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void servertabs$addSettingsButton(CallbackInfo ci) {
        int multiplayerY = this.height / 4 + 48 + 24;
        int buttonX     = this.width  / 2 + 102;

        this.addRenderableWidget(
            Button.builder(
                Component.literal("\u2699"),          // ⚙ gear symbol (Unicode U+2699)
                btn -> this.minecraft.setScreen(
                    new ServerTabsSettingsScreen(this) // open our settings screen
                )
            )
            .bounds(buttonX, multiplayerY, 20, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("ServerTabs Settings")
            ))
            .build()
        );
    }
}
