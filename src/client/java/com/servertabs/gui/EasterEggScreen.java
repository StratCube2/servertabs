package com.servertabs.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Easter egg modal — triggered after clicking the tab dropdown toggle
 * 10 times without switching tabs.
 *
 * Displays a centered dialog with a message and an OK button.
 */
public class EasterEggScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 80;

    private static final String MESSAGE_LINE_1 = "bro clicked the button";
    private static final String MESSAGE_LINE_2 = "10 times... Zleo lowk stupid";

    private final Screen parent;

    public EasterEggScreen(Screen parent) {
        super(Component.literal("..."));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int px = (this.width  - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("ok fine"),
                btn -> this.minecraft.setScreen(parent))
                .bounds(px + PANEL_W / 2 - 30, py + PANEL_H - 24, 60, 16)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // Dim the background without the full blur — keeps JMS visible behind
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int px = (this.width  - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // Panel background + border
        g.fill(px,              py,              px + PANEL_W, py + PANEL_H, 0xEE101018);
        g.fill(px,              py,              px + PANEL_W, py + 1,       0xFF777799);
        g.fill(px,              py + PANEL_H -1, px + PANEL_W, py + PANEL_H, 0xFF777799);
        g.fill(px,              py,              px + 1,        py + PANEL_H, 0xFF777799);
        g.fill(px + PANEL_W -1, py,              px + PANEL_W, py + PANEL_H, 0xFF777799);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        int cx = this.width / 2;
        int py = (this.height - PANEL_H) / 2;

        g.centeredText(this.font, MESSAGE_LINE_1, cx, py + 12, 0xFFAAAAFF);
        g.centeredText(this.font, MESSAGE_LINE_2, cx, py + 26, 0xFFFFFFFF);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void    onClose()          { this.minecraft.setScreen(parent); }

    // Block clicks from passing through to the screen behind
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        return super.mouseClicked(event, bl);
    }
}
