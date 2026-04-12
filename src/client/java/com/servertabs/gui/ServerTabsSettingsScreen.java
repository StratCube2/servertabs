package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class ServerTabsSettingsScreen extends Screen {
    private final Screen parent;
    private int leftX, leftY, leftW, leftH, selectedIndex = -1;
    private Button editBtn, deleteBtn;

    public ServerTabsSettingsScreen(Screen parent) {
        super(Component.literal("ServerTabs Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftX = 10; leftY = 30; leftW = width / 2 - 20; leftH = height - 60;
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
            minecraft.setScreen(new TabNamePopupScreen(this, "Add Tab", "", name -> {
                TabConfig.getInstance().addTab(name);
                TabConfig.getInstance().save();
            }));
        }).bounds(leftX, leftY + leftH - 20, 40, 20).build());

        editBtn = Button.builder(Component.literal("Edit"), b -> {
            TabEntry t = TabConfig.getInstance().getTabs().get(selectedIndex);
            minecraft.setScreen(new TabNamePopupScreen(this, "Rename", t.getName(), name -> {
                TabConfig.getInstance().renameTab(t, name);
                TabConfig.getInstance().save();
            }));
        }).bounds(leftX + 45, leftY + leftH - 20, 40, 20).build();
        addRenderableWidget(editBtn);

        deleteBtn = Button.builder(Component.literal("Del"), b -> {
            TabConfig.getInstance().deleteTab(TabConfig.getInstance().getTabs().get(selectedIndex));
            TabConfig.getInstance().save();
            selectedIndex = -1;
        }).bounds(leftX + 90, leftY + leftH - 20, 40, 20).build();
        addRenderableWidget(deleteBtn);

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 50, height - 25, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        for (int i = 0; i < tabs.size(); i++) {
            int ty = leftY + i * 20;
            if (i == selectedIndex) g.fill(leftX, ty, leftX + leftW, ty + 20, 0x44FFFFFF);
            g.text(font, tabs.get(i).getName(), leftX + 5, ty + 5, 0xFFFFFFFF, false);
        }
        editBtn.active = deleteBtn.active = (selectedIndex >= 0 && !tabs.get(selectedIndex).isLocked());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean b) {
        if (e.x() >= leftX && e.x() < leftX + leftW && e.y() >= leftY && e.y() < leftY + leftH - 25) {
            selectedIndex = (int) (e.y() - leftY) / 20;
            if (selectedIndex >= TabConfig.getInstance().getTabs().size()) selectedIndex = -1;
            return true;
        }
        return super.mouseClicked(e, b);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}