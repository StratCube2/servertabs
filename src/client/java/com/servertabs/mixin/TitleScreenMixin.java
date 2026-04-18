package com.servertabs.mixin;

import com.servertabs.gui.ServerTabsSettingsScreen;
import com.servertabs.gui.WorldTabsSettingsScreen;
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

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /**
     * Injected at the very end of TitleScreen.init().
     *
     * Vanilla button positions (200 px wide, centred):
     *   Singleplayer  y = this.height/4 + 48
     *   Multiplayer   y = this.height/4 + 48 + 24
     *
     * We place a ⚙ gear next to the Singleplayer button (WorldTabs settings)
     * and another next to the Multiplayer button (ServerTabs settings).
     * Both buttons are 20×20 and start 2px after the right edge of their row.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void servertabs$addSettingsButtons(CallbackInfo ci) {
        int buttonX = this.width / 2 + 102;

        // ── ServerTabs gear (next to Multiplayer button) ──────────────────
        int multiplayerY = this.height / 4 + 48 + 24;
        this.addRenderableWidget(
            Button.builder(
                Component.literal("\u2699"),
                btn -> this.minecraft.setScreen(new ServerTabsSettingsScreen(this))
            )
            .bounds(buttonX, multiplayerY, 20, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("ServerTabs Settings")
            ))
            .build()
        );

        // ── WorldTabs gear (next to Singleplayer button) ──────────────────
        int singleplayerY = this.height / 4 + 48;
        this.addRenderableWidget(
            Button.builder(
                Component.literal("\u2699"),
                btn -> this.minecraft.setScreen(new WorldTabsSettingsScreen(this))
            )
            .bounds(buttonX, singleplayerY, 20, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("WorldTabs Settings")
            ))
            .build()
        );
    }
}
