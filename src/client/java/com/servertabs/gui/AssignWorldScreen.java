package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shows a list of world tabs with checkboxes.
 * Tick/untick any non-locked tab to assign or unassign the world.
 *
 * "All" is always ticked and cannot be unticked — every world is always in All.
 */
public class AssignWorldScreen extends Screen {

    private static final int ROW_H    = 22;
    private static final int BOX_SIZE = 11;
    private static final int PANEL_W  = 220;

    private final Screen parent;
    private final String worldId;
    private final String worldName;

    public AssignWorldScreen(Screen parent, String worldId, String worldName) {
        super(Component.literal("Assign World to Tabs"));
        this.parent    = parent;
        this.worldId   = worldId;
        this.worldName = worldName;
    }

    // -----------------------------------------------------------------------
    //  Init
    // -----------------------------------------------------------------------

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                btn -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Deselect All"),
                btn -> {
                    if (!worldId.isEmpty()) {
                        TabConfig.getInstance().deselectAllWorldTabs(worldId);
                    }
                })
                .bounds(this.width / 2 - 50, this.height - 55, 100, 20)
                .build());
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        g.fill(px,               py,              px + PANEL_W, py + panelH, 0xEE101010);
        g.fill(px,               py,              px + PANEL_W, py + 1,      0xFF666666);
        g.fill(px,               py + panelH - 1, px + PANEL_W, py + panelH, 0xFF666666);
        g.fill(px,               py,              px + 1,        py + panelH, 0xFF666666);
        g.fill(px + PANEL_W - 1, py,              px + PANEL_W,  py + panelH, 0xFF666666);
        g.fill(px + 1, py + 18,  px + PANEL_W - 1, py + 19, 0xFF444444);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        // Title
        g.drawCenteredString(this.font, this.title, this.width / 2, py + 5, 0xFFFFFFFF);

        // World subtitle
        String subtitle = worldName.isEmpty() ? worldId : worldName + "  (" + worldId + ")";
        if (this.font.width(subtitle) > PANEL_W - 16) {
            subtitle = this.font.plainSubstrByWidth(subtitle, PANEL_W - 16) + "\u2026";
        }
        g.drawCenteredString(this.font, Component.literal(subtitle),
                this.width / 2, py + 22, 0xFF888888);

        // Warning if world ID is empty
        if (worldId.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.literal("No world ID provided!"),
                    this.width / 2, py + 40, 0xFFFF5555);
            return;
        }

        // Checkbox rows
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab     = tabs.get(i);
            int      rowX    = px + 10;
            int      rowY    = py + 32 + i * ROW_H;
            boolean  checked = tab.isLocked() || TabConfig.getInstance().worldInTab(worldId, tab.getId());
            boolean  locked  = tab.isLocked();
            boolean  hovered = !locked
                            && mx >= rowX && mx < px + PANEL_W - 10
                            && my >= rowY && my < rowY + ROW_H;

            if (hovered) g.fill(px + 1, rowY, px + PANEL_W - 1, rowY + ROW_H, 0xFF1E2E3E);

            // Checkbox border + fill
            int boxX      = rowX;
            int boxY      = rowY + (ROW_H - BOX_SIZE) / 2;
            int borderCol = locked ? 0xFF666666 : (hovered ? 0xFF99BBFF : 0xFF888888);
            g.fill(boxX,          boxY,          boxX + BOX_SIZE,     boxY + BOX_SIZE, borderCol);
            g.fill(boxX + 1,      boxY + 1,      boxX + BOX_SIZE - 1, boxY + BOX_SIZE - 1,
                   checked ? (locked ? 0xFF557755 : 0xFF44AA44) : 0xFF1A1A1A);

            // Checkmark — two overlapping filled rectangles forming a ✓
            if (checked) {
                g.fill(boxX + 2, boxY + 5, boxX + 5,            boxY + BOX_SIZE - 1, 0xFFFFFFFF);
                g.fill(boxX + 4, boxY + 2, boxX + BOX_SIZE - 1, boxY + 6,            0xFFFFFFFF);
            }

            // Tab name
            int labelX    = boxX + BOX_SIZE + 6;
            int textColor = locked  ? 0xFFFFDD88
                          : checked ? 0xFFFFFFFF
                          :           0xFFAAAAAA;
            g.drawString(this.font, tab.getName(),
                    labelX, rowY + (ROW_H - 8) / 2, textColor, false);

            // "(always)" badge on the locked All tab
            if (locked) {
                int badge = labelX + this.font.width(tab.getName()) + 5;
                g.drawString(this.font, "(always)", badge,
                        rowY + (ROW_H - 8) / 2, 0xFF555555, false);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (worldId.isEmpty()) return super.mouseClicked(event, bl);

        double mx = event.x();
        double my = event.y();

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab = tabs.get(i);
            if (tab.isLocked()) continue;

            int rowY = py + 32 + i * ROW_H;
            if (mx >= px + 10 && mx < px + PANEL_W - 10
             && my >= rowY    && my < rowY + ROW_H) {
                TabConfig.getInstance().toggleWorldTab(worldId, tab.getId());
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void    onClose()          { this.minecraft.setScreen(parent); }
}
