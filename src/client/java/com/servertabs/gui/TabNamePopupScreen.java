package com.servertabs.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A small popup screen used for both "Add Tab" and "Rename Tab".
 * Renders as a modal dialog over its parent screen.
 *
 * @param parent    screen to return to on close/cancel
 * @param title     title displayed at the top of the popup (e.g. "New Tab")
 * @param initial   pre-filled value for the text field (empty string for Add)
 * @param onConfirm callback called with the trimmed text when OK is pressed
 */
public class TabNamePopupScreen extends Screen {

    private static final int POPUP_W = 180;
    private static final int POPUP_H = 76;

    private final Screen           parent;
    private final String           initial;
    private final Consumer<String> onConfirm;

    private EditBox nameField;

    public TabNamePopupScreen(Screen parent, String title,
                              String initial, Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent    = parent;
        this.initial   = initial != null ? initial : "";
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int px = (this.width  - POPUP_W) / 2;
        int py = (this.height - POPUP_H) / 2;

        nameField = new EditBox(this.font,
                px + 8, py + 22, POPUP_W - 16, 16,
                Component.literal("Tab name"));
        nameField.setMaxLength(32);
        nameField.setValue(initial);
        nameField.setFocused(true);
        this.addRenderableWidget(nameField);

        // OK
        this.addRenderableWidget(Button.builder(
                Component.literal("OK"),
                btn -> confirm())
                .bounds(px + 8, py + 46, 76, 18)
                .build());

        // Cancel
        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(parent))
                .bounds(px + 96, py + 46, 76, 18)
                .build());
    }

    private void confirm() {
        String value = nameField.getValue().trim();
        if (!value.isEmpty()) {
            onConfirm.accept(value);
        }
        this.minecraft.setScreen(parent);
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    // FIX 1: changed "protected" → "public" to match the parent class signature
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt); // single blur call

        int px = (this.width  - POPUP_W) / 2;
        int py = (this.height - POPUP_H) / 2;

        // Popup background
        g.fill(px, py, px + POPUP_W, py + POPUP_H, 0xEE1A1A1A);
        // Border
        g.fill(px,               py,               px + POPUP_W,     py + 1,            0xFF777777);
        g.fill(px,               py + POPUP_H - 1, px + POPUP_W,     py + POPUP_H,      0xFF777777);
        g.fill(px,               py,               px + 1,            py + POPUP_H,      0xFF777777);
        g.fill(px + POPUP_W - 1, py,               px + POPUP_W,     py + POPUP_H,      0xFF777777);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt); // background + widgets

        int py = (this.height - POPUP_H) / 2;
        g.drawCenteredString(this.font, this.title, this.width / 2, py + 8, 0xFF000000 | 0xFFFFFF);
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    // FIX 2: updated keyPressed signature to accept KeyEvent instead of (int, int, int)
    // KeyEvent is a Java record with Mojang-mapped component names
    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            confirm();
            return true;
        }
        if (keyCode == 256) { // Escape
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // handled manually above so we go back to parent
    }
}
