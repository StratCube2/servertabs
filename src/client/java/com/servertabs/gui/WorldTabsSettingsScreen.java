package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * WorldTabs Settings Screen
 *
 * Layout:
 *   Left panel  — scrollable world-tab list + Add / Edit / Delete buttons
 *   Right panel — global world-tab settings (dropdown toggle, speed, sort, default tab, etc.)
 *   Bottom      — Done button
 *
 * Keyboard:
 *   Alt + Up / Down  — reorder the currently selected world tab
 */
public class WorldTabsSettingsScreen extends Screen {

    private static final int ROW_H        = 18;
    private static final int PANEL_BORDER = 0xFF555555;
    private static final int PANEL_BG     = 0xC0101010;
    private static final int DIVIDER      = 0xFF444444;

    private final Screen parent;

    private int leftX,  leftY,  leftW,  leftH;
    private int rightX, rightY, rightW, rightH;
    private int listY,  listH;

    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    private Button editBtn;
    private Button deleteBtn;
    private Button enabledBtn;
    private Button dropdownBtn;
    private Button speedBtn;
    private Button sortBtn;
    private Button defaultTabBtn;
    private Button rememberTabBtn;
    private Button assignOnAddBtn;

    public WorldTabsSettingsScreen(Screen parent) {
        super(Component.literal("WorldTabs Settings"));
        this.parent = parent;
    }

    // -----------------------------------------------------------------------
    //  init()
    // -----------------------------------------------------------------------

    @Override
    protected void init() {
        leftX  = 5;
        leftY  = 22;
        leftW  = this.width / 2 - 8;
        leftH  = this.height - 57;

        rightX = this.width / 2 + 3;
        rightY = 22;
        rightW = this.width - rightX - 5;
        rightH = this.height - 57;

        listY = leftY + 15;
        listH = leftH - 38;

        // ---- Left panel buttons ----
        int btnY   = leftY + leftH - 20;
        int btnW   = (leftW - 8) / 3;
        int btnGap = 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("Add"),
                btn -> openAddPopup())
                .bounds(leftX + 2, btnY, btnW, 16)
                .build());

        editBtn = Button.builder(
                Component.literal("Edit"),
                btn -> openEditPopup())
                .bounds(leftX + 2 + btnW + btnGap, btnY, btnW, 16)
                .build();
        this.addRenderableWidget(editBtn);

        deleteBtn = Button.builder(
                Component.literal("Delete"),
                btn -> deleteSelected())
                .bounds(leftX + 2 + (btnW + btnGap) * 2, btnY, btnW, 16)
                .build();
        this.addRenderableWidget(deleteBtn);

        // ---- Right panel settings ----
        int rBtnX = rightX + rightW / 2;
        int rBtnW = rightW / 2 - 4;

        enabledBtn = Button.builder(
                Component.literal(enabledLabel()),
                btn -> {
                    TabConfig.getInstance().setWorldTabsEnabled(
                            !TabConfig.getInstance().isWorldTabsEnabled());
                    btn.setMessage(Component.literal(enabledLabel()));
                })
                .bounds(rBtnX, rightY + 22, rBtnW, 14)
                .build();
        this.addRenderableWidget(enabledBtn);

        dropdownBtn = Button.builder(
                Component.literal(dropdownLabel()),
                btn -> {
                    TabConfig.getInstance().setWorldDropdownEnabled(
                            !TabConfig.getInstance().isWorldDropdownEnabled());
                    btn.setMessage(Component.literal(dropdownLabel()));
                })
                .bounds(rBtnX, rightY + 42, rBtnW, 14)
                .build();
        this.addRenderableWidget(dropdownBtn);

        speedBtn = Button.builder(
                Component.literal(TabConfig.getInstance().getWorldTransitionSpeed().label),
                btn -> {
                    TabConfig.TransitionSpeed next =
                            TabConfig.getInstance().getWorldTransitionSpeed().next();
                    TabConfig.getInstance().setWorldTransitionSpeed(next);
                    btn.setMessage(Component.literal(next.label));
                })
                .bounds(rBtnX, rightY + 62, rBtnW, 14)
                .build();
        this.addRenderableWidget(speedBtn);

        sortBtn = Button.builder(
                Component.literal(TabConfig.getInstance().getWorldSortingType().label),
                btn -> {
                    TabConfig.WorldSortingType next =
                            TabConfig.getInstance().getWorldSortingType().next();
                    TabConfig.getInstance().setWorldSortingType(next);
                    btn.setMessage(Component.literal(next.label));
                })
                .bounds(rBtnX, rightY + 82, rBtnW, 14)
                .build();
        this.addRenderableWidget(sortBtn);

        defaultTabBtn = Button.builder(
                Component.literal(defaultTabLabel()),
                btn -> {
                    cycleDefaultTab();
                    btn.setMessage(Component.literal(defaultTabLabel()));
                })
                .bounds(rBtnX, rightY + 102, rBtnW, 14)
                .build();
        this.addRenderableWidget(defaultTabBtn);

        rememberTabBtn = Button.builder(
                Component.literal(rememberTabLabel()),
                btn -> {
                    TabConfig.getInstance().setWorldRememberTab(
                            !TabConfig.getInstance().isWorldRememberTab());
                    btn.setMessage(Component.literal(rememberTabLabel()));
                })
                .bounds(rBtnX, rightY + 122, rBtnW, 14)
                .build();
        this.addRenderableWidget(rememberTabBtn);

        assignOnAddBtn = Button.builder(
                Component.literal(assignOnAddLabel()),
                btn -> {
                    TabConfig.getInstance().setWorldAssignOnAdd(
                            !TabConfig.getInstance().isWorldAssignOnAdd());
                    btn.setMessage(Component.literal(assignOnAddLabel()));
                })
                .bounds(rBtnX, rightY + 142, rBtnW, 14)
                .build();
        this.addRenderableWidget(assignOnAddBtn);

        // ---- Done button ----
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                btn -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());

        refreshButtonStates();
    }

    // -----------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);

        drawPanel(g, leftX,  leftY,  leftW,  leftH);
        drawPanel(g, rightX, rightY, rightW, rightH);

        g.fill(leftX  + 1, leftY  + 14, leftX  + leftW  - 1, leftY  + 15, DIVIDER);
        g.fill(rightX + 1, rightY + 14, rightX + rightW - 1, rightY + 15, DIVIDER);
        g.fill(leftX + 1, leftY + leftH - 22,
               leftX + leftW - 1, leftY + leftH - 21, DIVIDER);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + h,     PANEL_BG);
        g.fill(x,         y,         x + w,     y + 1,     PANEL_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     PANEL_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     PANEL_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     PANEL_BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        g.drawString(this.font, "World Tabs", leftX  + 4, leftY  + 4, 0xFFFFFFFF, false);
        g.drawString(this.font, "Settings",   rightX + 4, rightY + 4, 0xFFFFFFFF, false);

        int lx = rightX + 5;
        g.drawString(this.font, "Enabled:",    lx, rightY + 25,  0xFFCCCCCC, false);
        g.drawString(this.font, "Dropdown:",   lx, rightY + 45,  0xFFCCCCCC, false);
        g.drawString(this.font, "Speed:",      lx, rightY + 65,  0xFFCCCCCC, false);
        g.drawString(this.font, "Sort By:",    lx, rightY + 85,  0xFFCCCCCC, false);
        g.drawString(this.font, "Default:",    lx, rightY + 105, 0xFFCCCCCC, false);
        g.drawString(this.font, "Rem. Tab:",   lx, rightY + 125, 0xFFCCCCCC, false);
        g.drawString(this.font, "Assign+Add:", lx, rightY + 145, 0xFFCCCCCC, false);

        drawTabList(g, mx, my);

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (selectedIndex > 0 && selectedIndex < tabs.size()
                && !tabs.get(selectedIndex).isLocked()) {
            g.drawCenteredString(this.font,
                    Component.literal("Alt + \u2191\u2193 to reorder"),
                    leftX + leftW / 2,
                    leftY + leftH - 34,
                    0xFF666666);
        }
    }

    private void drawTabList(GuiGraphics g, int mx, int my) {
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        int visibleRows = listH / ROW_H;
        int maxScroll   = Math.max(0, tabs.size() - visibleRows);
        scrollOffset    = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < visibleRows; i++) {
            int tabIdx = i + scrollOffset;
            if (tabIdx >= tabs.size()) break;

            TabEntry tab = tabs.get(tabIdx);
            int rowX = leftX + 2;
            int rowY = listY + i * ROW_H;
            int rowW = leftW - 4;

            boolean isSelected = tabIdx == selectedIndex;
            boolean isHovered  = mx >= rowX && mx < rowX + rowW
                              && my >= rowY && my < rowY + ROW_H;

            int bg = isSelected ? 0xFF2A4A2A
                   : isHovered  ? 0xFF1E2E3E
                   :              0x00000000;
            if (bg != 0) g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H, bg);

            if (isSelected) g.fill(rowX, rowY, rowX + 2, rowY + ROW_H, 0xFF55BB55);

            String label     = tab.isLocked() ? "\u2605 " + tab.getName() : tab.getName();
            int    textColor = tab.isLocked()
                             ? (isSelected ? 0xFFFFFFAA : 0xFFFFDD88)
                             : (isSelected ? 0xFFFFFFFF : 0xFFCCCCCC);
            g.drawString(this.font, label,
                    rowX + 6, rowY + (ROW_H - 8) / 2, textColor, false);
        }

        if (scrollOffset > 0) {
            g.drawString(this.font, "\u25B2",
                    leftX + leftW - 10, listY + 2, 0xFF888888, false);
        }
        if (scrollOffset < maxScroll) {
            g.drawString(this.font, "\u25BC",
                    leftX + leftW - 10, listY + listH - 10, 0xFF888888, false);
        }
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (mx >= leftX + 2 && mx < leftX + leftW - 2
         && my >= listY     && my < listY + listH) {

            int row    = (int)((my - listY) / ROW_H);
            int tabIdx = row + scrollOffset;
            List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();

            if (tabIdx >= 0 && tabIdx < tabs.size()) {
                selectedIndex = tabIdx;
                refreshButtonStates();
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= leftX && mx < leftX + leftW && my >= leftY && my < leftY + leftH) {
            List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
            int visibleRows = listH / ROW_H;
            int maxScroll   = Math.max(0, tabs.size() - visibleRows);
            scrollOffset    = Math.max(0, Math.min(maxScroll,
                                scrollOffset - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        boolean altDown = (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0;

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();

        if (altDown && selectedIndex >= 0 && selectedIndex < tabs.size()) {
            if (keyCode == GLFW.GLFW_KEY_UP && selectedIndex > 0
                    && !tabs.get(selectedIndex - 1).isLocked()) {
                TabConfig.getInstance().moveWorldTabUp(selectedIndex);
                selectedIndex--;
                clampScrollToSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN
                    && selectedIndex < tabs.size() - 1
                    && !tabs.get(selectedIndex).isLocked()) {
                TabConfig.getInstance().moveWorldTabDown(selectedIndex);
                selectedIndex++;
                clampScrollToSelection();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    // -----------------------------------------------------------------------
    //  Tab operations
    // -----------------------------------------------------------------------

    private void openAddPopup() {
        this.minecraft.setScreen(new TabNamePopupScreen(this, "New World Tab", "", name -> {
            TabConfig.getInstance().addWorldTab(name);
            selectedIndex = TabConfig.getInstance().getWorldTabs().size() - 1;
            clampScrollToSelection();
            refreshButtonStates();
            refreshDefaultTabBtn();
        }));
    }

    private void openEditPopup() {
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (selectedIndex < 0 || selectedIndex >= tabs.size()) return;
        TabEntry tab = tabs.get(selectedIndex);
        if (tab.isLocked()) return;

        this.minecraft.setScreen(new TabNamePopupScreen(this, "Rename World Tab", tab.getName(), name -> {
            TabConfig.getInstance().renameWorldTab(tab, name);
            refreshDefaultTabBtn();
        }));
    }

    private void deleteSelected() {
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (selectedIndex < 0 || selectedIndex >= tabs.size()) return;
        TabEntry tab = tabs.get(selectedIndex);
        if (tab.isLocked()) return;

        TabConfig.getInstance().deleteWorldTab(tab);
        if (selectedIndex >= tabs.size()) selectedIndex = tabs.size() - 1;
        refreshButtonStates();
        refreshDefaultTabBtn();
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private void refreshButtonStates() {
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        boolean hasMovable = selectedIndex >= 0
                          && selectedIndex < tabs.size()
                          && !tabs.get(selectedIndex).isLocked();
        editBtn.active   = hasMovable;
        deleteBtn.active = hasMovable;
    }

    private void refreshDefaultTabBtn() {
        if (defaultTabBtn != null)
            defaultTabBtn.setMessage(Component.literal(defaultTabLabel()));
    }

    private String enabledLabel()     { return TabConfig.getInstance().isWorldTabsEnabled()    ? "ON" : "OFF"; }
    private String dropdownLabel()    { return TabConfig.getInstance().isWorldDropdownEnabled() ? "ON" : "OFF"; }
    private String rememberTabLabel() { return TabConfig.getInstance().isWorldRememberTab()     ? "ON" : "OFF"; }
    private String assignOnAddLabel() { return TabConfig.getInstance().isWorldAssignOnAdd()     ? "ON" : "OFF"; }

    private String defaultTabLabel() {
        TabEntry def = TabConfig.getInstance().getWorldDefaultTab();
        return def != null ? def.getName() : "All";
    }

    private void cycleDefaultTab() {
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (tabs.isEmpty()) return;
        String currentId = TabConfig.getInstance().getWorldDefaultTabId();
        int idx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(currentId)) { idx = i; break; }
        }
        TabConfig.getInstance().setWorldDefaultTabId(tabs.get((idx + 1) % tabs.size()).getId());
        refreshDefaultTabBtn();
    }

    private void clampScrollToSelection() {
        int visibleRows = listH / ROW_H;
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + visibleRows)
            scrollOffset = selectedIndex - visibleRows + 1;
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void    onClose()          { this.minecraft.setScreen(parent); }
}
