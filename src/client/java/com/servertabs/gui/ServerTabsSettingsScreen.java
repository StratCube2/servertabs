package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW; // still needed for GLFW_KEY_UP / GLFW_KEY_DOWN

import java.util.List;

/**
 * ServerTabs Settings Screen
 *
 * Layout:
 *   Left panel  — scrollable tab list + Add / Edit / Delete buttons
 *   Right panel — global settings (dropdown toggle, speed, sort, default tab)
 *   Bottom      — Done button
 *
 * Keyboard:
 *   Alt + Up / Down  — reorder the currently selected tab
 */
public class ServerTabsSettingsScreen extends Screen {

    private static final int ROW_H        = 18;
    private static final int PANEL_BORDER = 0xFF555555;
    private static final int PANEL_BG     = 0xC0101010;
    private static final int DIVIDER      = 0xFF444444;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private final Screen parent;

    // Panel geometry — computed in init()
    private int leftX,  leftY,  leftW,  leftH;
    private int rightX, rightY, rightW, rightH;
    private int listY,  listH;

    // Tab list
    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    // Button refs for enable/disable toggling
    private Button editBtn;
    private Button deleteBtn;
    private Button dropdownBtn;
    private Button speedBtn;
    private Button sortBtn;
    private Button defaultTabBtn;
    private Button rememberTabBtn;
    private Button assignOnAddBtn;

    // -----------------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------------

    public ServerTabsSettingsScreen(Screen parent) {
        super(Component.literal("ServerTabs Settings"));
        this.parent = parent;
    }

    // -----------------------------------------------------------------------
    //  init()
    // -----------------------------------------------------------------------

    @Override
    protected void init() {
        // Panel geometry
        leftX  = 5;
        leftY  = 22;
        leftW  = this.width / 2 - 8;
        leftH  = this.height - 57;

        rightX = this.width / 2 + 3;
        rightY = 22;
        rightW = this.width - rightX - 5;
        rightH = this.height - 57;

        // Scrollable list: inside left panel, below header, above action buttons
        listY = leftY + 15;
        listH = leftH - 38;

        // ---- Left panel buttons (bottom row) ----
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

        // ---- Right panel: global settings (label + cycle button per row) ----
        int rBtnX = rightX + rightW / 2;
        int rBtnW = rightW / 2 - 4;

        dropdownBtn = Button.builder(
                Component.literal(dropdownLabel()),
                btn -> {
                    TabConfig.getInstance().setDropdownEnabled(
                            !TabConfig.getInstance().isDropdownEnabled());
                    btn.setMessage(Component.literal(dropdownLabel()));
                })
                .bounds(rBtnX, rightY + 22, rBtnW, 14)
                .build();
        this.addRenderableWidget(dropdownBtn);

        speedBtn = Button.builder(
                Component.literal(TabConfig.getInstance().getTransitionSpeed().label),
                btn -> {
                    TabConfig.TransitionSpeed next =
                            TabConfig.getInstance().getTransitionSpeed().next();
                    TabConfig.getInstance().setTransitionSpeed(next);
                    btn.setMessage(Component.literal(next.label));
                })
                .bounds(rBtnX, rightY + 42, rBtnW, 14)
                .build();
        this.addRenderableWidget(speedBtn);

        sortBtn = Button.builder(
                Component.literal(TabConfig.getInstance().getSortingType().label),
                btn -> {
                    TabConfig.SortingType next =
                            TabConfig.getInstance().getSortingType().next();
                    TabConfig.getInstance().setSortingType(next);
                    btn.setMessage(Component.literal(next.label));
                })
                .bounds(rBtnX, rightY + 62, rBtnW, 14)
                .build();
        this.addRenderableWidget(sortBtn);

        defaultTabBtn = Button.builder(
                Component.literal(defaultTabLabel()),
                btn -> {
                    cycleDefaultTab();
                    btn.setMessage(Component.literal(defaultTabLabel()));
                })
                .bounds(rBtnX, rightY + 82, rBtnW, 14)
                .build();
        this.addRenderableWidget(defaultTabBtn);

        rememberTabBtn = Button.builder(
                Component.literal(rememberTabLabel()),
                btn -> {
                    TabConfig.getInstance().setRememberTab(
                            !TabConfig.getInstance().isRememberTab());
                    btn.setMessage(Component.literal(rememberTabLabel()));
                })
                .bounds(rBtnX, rightY + 102, rBtnW, 14)
                .build();
        this.addRenderableWidget(rememberTabBtn);

        assignOnAddBtn = Button.builder(
                Component.literal(assignOnAddLabel()),
                btn -> {
                    TabConfig.getInstance().setAssignOnAdd(
                            !TabConfig.getInstance().isAssignOnAdd());
                    btn.setMessage(Component.literal(assignOnAddLabel()));
                })
                .bounds(rBtnX, rightY + 122, rBtnW, 14)
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

    /**
     * Panel backgrounds are drawn here — before widgets — by overriding
     * renderBackground(). super.extractBackground() fires the blur exactly once.
     */
    // FIX 1: changed "protected" → "public" to match the parent class signature
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);

        drawPanel(g, leftX,  leftY,  leftW,  leftH);
        drawPanel(g, rightX, rightY, rightW, rightH);

        // Header dividers
        g.fill(leftX  + 1, leftY  + 14, leftX  + leftW  - 1, leftY  + 15, DIVIDER);
        g.fill(rightX + 1, rightY + 14, rightX + rightW - 1, rightY + 15, DIVIDER);

        // Separator above left-panel action buttons
        g.fill(leftX + 1, leftY + leftH - 22,
               leftX + leftW - 1, leftY + leftH - 21, DIVIDER);
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x,         y,         x + w,     y + h,     PANEL_BG);
        g.fill(x,         y,         x + w,     y + 1,     PANEL_BORDER);
        g.fill(x,         y + h - 1, x + w,     y + h,     PANEL_BORDER);
        g.fill(x,         y,         x + 1,     y + h,     PANEL_BORDER);
        g.fill(x + w - 1, y,         x + w,     y + h,     PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt); // panels + all widgets drawn

        // Screen title
        g.centeredText(this.font, this.title, this.width / 2, 8, 0xFF000000 | 0xFFFFFF);

        // Panel headers
        g.text(this.font, "Tabs",     leftX  + 4, leftY  + 4, 0xFF000000 | 0xFFFFFF, false);
        g.text(this.font, "Settings", rightX + 4, rightY + 4, 0xFF000000 | 0xFFFFFF, false);

        // Right-panel setting labels
        int lx = rightX + 5;
        g.text(this.font, "Dropdown:", lx, rightY + 25, 0xFF000000 | 0xCCCCCC, false);
        g.text(this.font, "Speed:",    lx, rightY + 45, 0xFF000000 | 0xCCCCCC, false);
        g.text(this.font, "Sort By:",  lx, rightY + 65, 0xFF000000 | 0xCCCCCC, false);
        g.text(this.font, "Default:",  lx, rightY + 85, 0xFF000000 | 0xCCCCCC, false);
        g.text(this.font, "Rem. Tab:", lx, rightY + 105, 0xFF000000 | 0xCCCCCC, false);
        g.text(this.font, "Assign+Add:", lx, rightY + 125, 0xFF000000 | 0xCCCCCC, false);

        // Tab list rows
        drawTabList(g, mx, my);

        // Alt+arrows hint when a movable tab is selected
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (selectedIndex > 0 && selectedIndex < tabs.size()
                && !tabs.get(selectedIndex).isLocked()) {
            g.centeredText(this.font,
                    Component.literal("Alt + \u2191\u2193 to reorder"),
                    leftX + leftW / 2,
                    leftY + leftH - 34,
                    0xFF000000 | 0x666666);
        }
    }

    private void drawTabList(GuiGraphicsExtractor g, int mx, int my) {
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
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

            // Row background
            int bg = isSelected ? 0xFF2A4A2A
                   : isHovered  ? 0xFF1E2E3E
                   :              0x00000000;
            if (bg != 0) g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H, bg);

            // Green selection accent bar on the left edge
            if (isSelected) g.fill(rowX, rowY, rowX + 2, rowY + ROW_H, 0xFF55BB55);

            // ★ prefix for the locked "All" tab
            String label     = tab.isLocked() ? "\u2605 " + tab.getName() : tab.getName();
            int    textColor = tab.isLocked()
                             ? (isSelected ? 0xFF000000 | 0xFFFFAA : 0xFF000000 | 0xFFDD88)
                             : (isSelected ? 0xFF000000 | 0xFFFFFF : 0xFF000000 | 0xCCCCCC);
            g.text(this.font, label,
                    rowX + 6, rowY + (ROW_H - 8) / 2, textColor, false);
        }

        // Scroll arrows
        if (scrollOffset > 0) {
            g.text(this.font, "\u25B2",
                    leftX + leftW - 10, listY + 2, 0xFF000000 | 0x888888, false);
        }
        if (scrollOffset < maxScroll) {
            g.text(this.font, "\u25BC",
                    leftX + leftW - 10, listY + listH - 10, 0xFF000000 | 0x888888, false);
        }
    }

    // -----------------------------------------------------------------------
    //  Input
    // -----------------------------------------------------------------------

    // FIX 2: updated mouseClicked signature to accept (MouseButtonEvent, boolean)
    // MouseButtonEvent is a Java record — x(), y(), button() are its Mojang-mapped accessors
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (mx >= leftX + 2 && mx < leftX + leftW - 2
         && my >= listY     && my < listY + listH) {

            int row    = (int)((my - listY) / ROW_H);
            int tabIdx = row + scrollOffset;
            List<TabEntry> tabs = TabConfig.getInstance().getTabs();

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
            List<TabEntry> tabs = TabConfig.getInstance().getTabs();
            int visibleRows = listH / ROW_H;
            int maxScroll   = Math.max(0, tabs.size() - visibleRows);
            scrollOffset    = Math.max(0, Math.min(maxScroll,
                                scrollOffset - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // FIX 3: updated keyPressed signature to accept KeyEvent
    // FIX 4: KeyEvent is a Java record; use Mojang-mapped accessors keyCode() and modifiers()
    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        boolean altDown = (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0;

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();

        if (altDown && selectedIndex >= 0 && selectedIndex < tabs.size()) {
            if (keyCode == GLFW.GLFW_KEY_UP && selectedIndex > 0
                    && !tabs.get(selectedIndex - 1).isLocked()) {
                TabConfig.getInstance().moveTabUp(selectedIndex);
                selectedIndex--;
                clampScrollToSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN
                    && selectedIndex < tabs.size() - 1
                    && !tabs.get(selectedIndex).isLocked()) {
                TabConfig.getInstance().moveTabDown(selectedIndex);
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
        this.minecraft.setScreen(new TabNamePopupScreen(this, "New Tab", "", name -> {
            TabConfig.getInstance().addTab(name);
            selectedIndex = TabConfig.getInstance().getTabs().size() - 1;
            clampScrollToSelection();
            refreshButtonStates();
            refreshDefaultTabBtn();
        }));
    }

    private void openEditPopup() {
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (selectedIndex < 0 || selectedIndex >= tabs.size()) return;
        TabEntry tab = tabs.get(selectedIndex);
        if (tab.isLocked()) return;

        this.minecraft.setScreen(new TabNamePopupScreen(this, "Rename Tab", tab.getName(), name -> {
            TabConfig.getInstance().renameTab(tab, name);
            refreshDefaultTabBtn();
        }));
    }

    private void deleteSelected() {
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (selectedIndex < 0 || selectedIndex >= tabs.size()) return;
        TabEntry tab = tabs.get(selectedIndex);
        if (tab.isLocked()) return;

        TabConfig.getInstance().deleteTab(tab);

        if (selectedIndex >= tabs.size()) selectedIndex = tabs.size() - 1;
        refreshButtonStates();
        refreshDefaultTabBtn();
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private void refreshButtonStates() {
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
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

    private String dropdownLabel() {
        return TabConfig.getInstance().isDropdownEnabled() ? "ON" : "OFF";
    }

    private String defaultTabLabel() {
        TabEntry def = TabConfig.getInstance().getDefaultTab();
        return def != null ? def.getName() : "All";
    }

    private String rememberTabLabel() {
        return TabConfig.getInstance().isRememberTab() ? "ON" : "OFF";
    }

    private String assignOnAddLabel() {
        return TabConfig.getInstance().isAssignOnAdd() ? "ON" : "OFF";
    }

    private void cycleDefaultTab() {
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (tabs.isEmpty()) return;
        String currentId = TabConfig.getInstance().getDefaultTabId();
        int idx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(currentId)) { idx = i; break; }
        }
        TabConfig.getInstance().setDefaultTabId(tabs.get((idx + 1) % tabs.size()).getId());
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
