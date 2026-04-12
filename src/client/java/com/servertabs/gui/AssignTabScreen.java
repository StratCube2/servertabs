package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

public class AssignTabScreen extends Screen {
    private static final int ROW_H = 22, BOX_SIZE = 11, PANEL_W = 220;
    private final Screen parent;
    private final String ip, name;

    public AssignTabScreen(Screen parent, String ip, String name) {
        super(Component.literal("Assign to Tabs"));
        this.parent = parent; this.ip = ip == null ? "" : ip; this.name = name == null ? "" : name;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.minecraft.setScreen(parent))
            .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);
        int ph = 40 + TabConfig.getInstance().getTabs().size() * ROW_H;
        int px = (width - PANEL_W) / 2, py = (height - ph) / 2;
        g.fill(px, py, px + PANEL_W, py + ph, 0xEE101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int ph = 40 + tabs.size() * ROW_H;
        int px = (width - PANEL_W) / 2, py = (height - ph) / 2;
        g.centeredText(font, title, width / 2, py + 10, 0xFFFFFFFF);
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry t = tabs.get(i);
            int ty = py + 35 + i * ROW_H;
            boolean checked = t.isLocked() || TabConfig.getInstance().serverInTab(ip, t.getId());
            g.fill(px + 10, ty, px + 20, ty + 10, 0xFF888888);
            g.fill(px + 11, ty + 1, px + 19, ty + 9, checked ? 0xFF44AA44 : 0xFF1A1A1A);
            g.text(font, t.getName(), px + 25, ty + 1, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int ph = 40 + tabs.size() * ROW_H;
        int px = (width - PANEL_W) / 2, py = (height - ph) / 2;
        for (int i = 0; i < tabs.size(); i++) {
            int ty = py + 35 + i * ROW_H;
            if (mx >= px + 10 && mx < px + PANEL_W - 10 && my >= ty && my < ty + ROW_H) {
                TabConfig.getInstance().toggleServerTab(ip, tabs.get(i).getId());
                TabConfig.getInstance().save(); // Crucial: manual save
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
}