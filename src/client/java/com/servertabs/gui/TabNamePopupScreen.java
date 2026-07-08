package com.servertabs.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * A small popup screen used for both "Add Tab" and "Rename Tab".
 * Features HEX Color assignment and preset color swatches.
 */
public class TabNamePopupScreen extends Screen {

    private static final int POPUP_W = 180;
    private static final int POPUP_H = 120; // Increased height to fit color swatches
    private static final int SWATCH_SIZE = 12;
    private static final int SWATCH_GAP = 4;

    private static final String[] PRESET_COLORS = {
        "#FF5555", // Red
        "#55FF55", // Green
        "#5555FF", // Blue
        "#FFFF55", // Yellow
        "#55FFFF", // Cyan
        "#FF55FF", // Purple
        "#FFFFFF", // White
        "#AAAAAA", // Gray
        "#111111"  // Black
    };

    private final Screen                       parent;
    private final String                       initialName;
    private final String                       initialColor;
    private final BiConsumer<String, String>   onConfirm;

    private EditBox nameField;
    private EditBox colorField;

    public TabNamePopupScreen(Screen parent, String title,
                              String initialName, String initialColor, 
                              BiConsumer<String, String> onConfirm) {
        super(Component.literal(title));
        this.parent       = parent;
        this.initialName  = initialName != null ? initialName : "";
        this.initialColor = initialColor != null ? initialColor : "#FFFFFF";
        this.onConfirm    = onConfirm;
    }

    @Override
    protected void init() {
        int px = (this.width  - POPUP_W) / 2;
        int py = (this.height - POPUP_H) / 2;

        nameField = new EditBox(this.font,
                px + 8, py + 24, POPUP_W - 16, 16,
                Component.literal("Tab Name"));
        nameField.setMaxLength(32);
        nameField.setValue(initialName);
        nameField.setFocused(true);
        this.addRenderableWidget(nameField);

        colorField = new EditBox(this.font,
                px + 8, py + 52, POPUP_W - 16, 16,
                Component.literal("Tab Color"));
        colorField.setMaxLength(7);
        colorField.setValue(initialColor);
        this.addRenderableWidget(colorField);

        // OK
        this.addRenderableWidget(Button.builder(
                Component.literal("OK"),
                btn -> confirm())
                .bounds(px + 8, py + 94, 76, 18)
                .build());

        // Cancel
        this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(parent))
                .bounds(px + 96, py + 94, 76, 18)
                .build());
    }

    private void confirm() {
        String nameVal = nameField.getValue().trim();
        String colVal  = colorField.getValue().trim();
        
        if (!colVal.startsWith("#") && !colVal.isEmpty()) {
            colVal = "#" + colVal;
        }
        
        if (!nameVal.isEmpty()) {
            onConfirm.accept(nameVal, colVal);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);

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
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);

        int px = (this.width  - POPUP_W) / 2;
        int py = (this.height - POPUP_H) / 2;
        
        g.centeredText(this.font, this.title, this.width / 2, py + 6, 0xFF000000 | 0xFFFFFF);
        
        g.text(this.font, "Name", px + 8, py + 15, 0xFF000000 | 0xAAAAAA, false);
        g.text(this.font, "HEX Color", px + 8, py + 43, 0xFF000000 | 0xAAAAAA, false);

        // Draw color swatches
        int swatchesW = PRESET_COLORS.length * SWATCH_SIZE + (PRESET_COLORS.length - 1) * SWATCH_GAP;
        int startX = px + (POPUP_W - swatchesW) / 2;
        int startY = py + 74;

        String currentHex = colorField.getValue().trim();

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = startX + i * (SWATCH_SIZE + SWATCH_GAP);
            
            int colorVal;
            try {
                colorVal = 0xFF000000 | Integer.parseInt(PRESET_COLORS[i].substring(1), 16);
            } catch (Exception e) {
                colorVal = 0xFFFFFFFF;
            }

            boolean isSelected = PRESET_COLORS[i].equalsIgnoreCase(currentHex);
            int borderColor = isSelected ? 0xFF55FF55 : 0xFF555555; // Bright green if selected, dark gray otherwise
            
            // Draw border
            g.fill(sx - 1, startY - 1, sx + SWATCH_SIZE + 1, startY + SWATCH_SIZE + 1, borderColor);
            // Draw fill
            g.fill(sx, startY, sx + SWATCH_SIZE, startY + SWATCH_SIZE, colorVal);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        int px = (this.width  - POPUP_W) / 2;
        int py = (this.height - POPUP_H) / 2;
        
        int swatchesW = PRESET_COLORS.length * SWATCH_SIZE + (PRESET_COLORS.length - 1) * SWATCH_GAP;
        int startX = px + (POPUP_W - swatchesW) / 2;
        int startY = py + 74;

        // Check if any swatch was clicked
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = startX + i * (SWATCH_SIZE + SWATCH_GAP);
            if (mx >= sx && mx < sx + SWATCH_SIZE && my >= startY && my < startY + SWATCH_SIZE) {
                colorField.setValue(PRESET_COLORS[i]);
                return true; // Consume event
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        
        // Switch Focus using TAB
        if (keyCode == 258) { 
            if (nameField.isFocused()) {
                nameField.setFocused(false);
                colorField.setFocused(true);
            } else {
                colorField.setFocused(false);
                nameField.setFocused(true);
            }
            return true;
        }
        
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
        return false;
    }
}