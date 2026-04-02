package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shows a list of tabs with checkboxes.
 * Tick/untick any non-locked tab to assign or unassign the server.
 * Every toggle saves immediately to TabConfig.
 *
 * "All" is always ticked and cannot be unticked — every server is always in All.
 */
public class AssignTabScreen extends Screen {

    private static final int ROW_H    = 22;
    private static final int BOX_SIZE = 11;
    private static final int PANEL_W  = 220;

    private final Screen parent;
    private final String serverIp;
    private final String serverName;

    public AssignTabScreen(Screen parent, String serverIp, String serverName) {
        super(Component.literal("Assign to Tabs"));
        this.parent     = parent;
        this.serverIp   = serverIp;
        this.serverName = serverName;
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
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        g.fill(px,              py,              px + PANEL_W, py + panelH, 0xEE101010);
        g.fill(px,              py,              px + PANEL_W, py + 1,      0xFF666666);
        g.fill(px,              py + panelH - 1, px + PANEL_W, py + panelH, 0xFF666666);
        g.fill(px,              py,              px + 1,        py + panelH, 0xFF666666);
        g.fill(px + PANEL_W - 1, py,             px + PANEL_W, py + panelH, 0xFF666666);
        g.fill(px + 1, py + 18, px + PANEL_W - 1, py + 19, 0xFF444444);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        // Title
        g.centeredText(this.font, this.title, this.width / 2, py + 5, 0xFFFFFFFF);

        // Server subtitle
        String subtitle = serverName.isEmpty() ? serverIp : serverName + "  (" + serverIp + ")";
        if (this.font.width(subtitle) > PANEL_W - 16) {
            subtitle = this.font.plainSubstrByWidth(subtitle, PANEL_W - 16) + "…";
        }
        g.centeredText(this.font, Component.literal(subtitle),
                this.width / 2, py + 22, 0xFF000000 | 0x888888);

        // Warning if IP is empty
        if (serverIp.isEmpty()) {
            g.centeredText(this.font,
                    Component.literal("Fill in the server IP first!"),
                    this.width / 2, py + 40, 0xFFFF5555);
            return;
        }

        // Checkbox rows
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab     = tabs.get(i);
            int      rowX    = px + 10;
            int      rowY    = py + 32 + i * ROW_H;
            boolean  checked = tab.isLocked() || TabConfig.getInstance().serverInTab(serverIp, tab.getId());
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
            int textColor = locked  ? 0xFF000000 | 0xFFDD88
                          : checked ? 0xFF000000 | 0xFFFFFF
                          :           0xFF000000 | 0xAAAAAA;
            g.text(this.font, tab.getName(),
                    labelX, rowY + (ROW_H - 8) / 2, textColor, false);

            // "(always)" badge on the locked All tab
            if (locked) {
                int badge = labelX + this.font.width(tab.getName()) + 5;
                g.text(this.font, "(always)", badge,
                        rowY + (ROW_H - 8) / 2, 0xFF000000 | 0x555555, false);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Input — uses MouseButtonEvent as required by 1.21.11 Fabric API
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (serverIp.isEmpty()) return super.mouseClicked(event, bl);

        double mx = event.x();
        double my = event.y();

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int panelH = 32 + tabs.size() * ROW_H + 8;
        int px     = (this.width  - PANEL_W) / 2;
        int py     = (this.height - panelH)  / 2;

        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab = tabs.get(i);
            if (tab.isLocked()) continue;

            int rowY = py + 32 + i * ROW_H;
            if (mx >= px + 10 && mx < px + PANEL_W - 10
             && my >= rowY    && my < rowY + ROW_H) {
                TabConfig.getInstance().toggleServerTab(serverIp, tab.getId());
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void    onClose()          { this.minecraft.setScreen(parent); }
}
