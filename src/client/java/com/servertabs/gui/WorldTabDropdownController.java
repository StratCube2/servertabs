package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import com.servertabs.WorldTabSessionState;
import com.servertabs.ServerTabsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldTabDropdownController {

    private static final int  PANEL_WIDTH       = 120;
    private static final int  PANEL_PAD         = 5;
    private static final int  TAB_HEIGHT        = 20;
    private static final int  TAB_GAP           = 3;
    private static final int  EASTER_EGG_CLICKS = 10;
    private static final int  CB_SIZE           = 10;
    private static final int  CB_MARGIN         = 4;   
    private static final int  ROW_H             = 36;  

    private final Screen screen;
    private boolean panelOpen     = false;
    private float   slideProgress = 0f;
    private String  activeTabId;
    private int     clickCount    = 0;

    private Button  toggleButton;
    private Button  deselectAllButton;

    private boolean     quickAssignMode    = false;
    private String      quickAssignTabId   = null;
    private Set<String> quickAssignChecked = new HashSet<>();

    private List<Object> cachedFullList = null;

    public WorldTabDropdownController(Screen screen) {
        this.screen      = screen;
        this.activeTabId = WorldTabSessionState.getActiveTabId();
    }

    public void setupWidgets(Screen screen) {
        panelOpen     = false;
        slideProgress = 0f;

        toggleButton = Button.builder(
                Component.literal("Tabs \u2193"),
                btn -> {
                    if (quickAssignMode) {
                        commitQuickAssign();
                        return;
                    }
                    if (!TabConfig.getInstance().isWorldDropdownEnabled()) return;
                    panelOpen = !panelOpen;
                    refreshToggleLabel();

                    clickCount++;
                    if (clickCount >= EASTER_EGG_CLICKS) {
                        clickCount = 0;
                        Minecraft.getInstance().setScreen(new EasterEggScreen(screen));
                    }
                })
                .bounds(4, 4, 60, 20)
                .build();

        deselectAllButton = Button.builder(
                Component.literal("Deselect All"),
                btn -> {
                    quickAssignChecked.clear();
                })
                .bounds(68, 4, 80, 20)
                .build();
        deselectAllButton.visible = false;

        Screens.getWidgets(screen).add(toggleButton);
        Screens.getWidgets(screen).add(deselectAllButton);

        refreshToggleLabel();
    }

    public void switchTab(int delta) {
        if (quickAssignMode) return;
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (tabs.isEmpty()) return;

        int currentIdx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(activeTabId)) { currentIdx = i; break; }
        }

        int newIdx  = Math.floorMod(currentIdx + delta, tabs.size());
        activeTabId = tabs.get(newIdx).getId();
        WorldTabSessionState.setActiveTabId(activeTabId);
        clickCount  = 0;
        panelOpen   = false;
        refreshToggleLabel();
        applyTabFilter(activeTabId);
    }

    public void onRender(Screen s, GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (quickAssignMode) {
            renderQuickAssignOverlay(gfx, mouseX, mouseY);
            return;
        }

        float speed = TabConfig.getInstance().getWorldTransitionSpeed().value;
        slideProgress = panelOpen ? Math.min(1f, slideProgress + speed) : Math.max(0f, slideProgress - speed);
        if (slideProgress <= 0f) return;
        if (!TabConfig.getInstance().isWorldDropdownEnabled()) { panelOpen = false; slideProgress = 0f; return; }

        float ease   = easeInOut(slideProgress);
        int   panelX = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs    = TabConfig.getInstance().getWorldTabs();
        int            tabAreaH = tabs.size() * (TAB_HEIGHT + TAB_GAP) - TAB_GAP;
        int            panelH  = PANEL_PAD * 2 + tabAreaH;

        gfx.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + panelH, 0xC8101010);
        int border = 0xFF555555;
        gfx.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + 1, border);
        gfx.fill(panelX, panelTop + panelH - 1, panelX + PANEL_WIDTH, panelTop + panelH, border);
        gfx.fill(panelX + PANEL_WIDTH - 1, panelTop, panelX + PANEL_WIDTH, panelTop + panelH, border);

        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab  = tabs.get(i);
            int tabX = panelX + PANEL_PAD;
            int tabY = panelTop + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tabW = PANEL_WIDTH - PANEL_PAD * 2;

            boolean isActive  = tab.getId().equals(activeTabId);
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bgColor = isActive ? 0xFF3A5C3A : isHovered ? 0xFF2E3A58 : 0xFF222222;
            gfx.fill(tabX, tabY, tabX + tabW, tabY + TAB_HEIGHT, bgColor);

            int borderC = isActive ? 0xFF66BB66 : 0xFF404040;
            gfx.fill(tabX, tabY, tabX + tabW, tabY + 1, borderC);
            gfx.fill(tabX, tabY + TAB_HEIGHT - 1, tabX + tabW, tabY + TAB_HEIGHT, borderC);
            gfx.fill(tabX, tabY, tabX + 1, tabY + TAB_HEIGHT, borderC);
            gfx.fill(tabX + tabW - 1, tabY, tabX + tabW, tabY + TAB_HEIGHT, borderC);

            gfx.text(Minecraft.getInstance().font, tab.getName(), tabX + 6, tabY + (TAB_HEIGHT - 8) / 2, isActive ? 0xFFFFFFFF : 0xFFAAAAAA, false);

            if (isHovered && !isActive && !tab.isLocked() && "all".equals(activeTabId)) {
                gfx.text(Minecraft.getInstance().font, "[Alt]", tabX + tabW - 28, tabY + (TAB_HEIGHT - 8) / 2, 0xFF000000 | 0x777777, false);
            }
        }
    }

    private void renderQuickAssignOverlay(GuiGraphics gfx, int mouseX, int mouseY) {
        Object listWidget = getListWidget(screen);
        if (listWidget == null) return;

        TabEntry target = TabConfig.getInstance().getWorldTabs().stream().filter(t -> t.getId().equals(quickAssignTabId)).findFirst().orElse(null);
        String header = "Quick Assign \u2192 " + (target != null ? target.getName() : "?");
        gfx.text(Minecraft.getInstance().font, header, 4, 30, 0xFF000000 | 0xFFDD44, false);

        try {
            Method childrenMethod = getMethodUpwards(listWidget.getClass(), "children");
            if (childrenMethod == null) return;
            List<?> children = (List<?>) childrenMethod.invoke(listWidget);
            
            Method getRowTop = getMethodUpwards(listWidget.getClass(), "getRowTop", int.class);
            if (getRowTop == null) return;
            getRowTop.setAccessible(true);
            
            int listX = 10;
            try {
                Method getX = getMethodUpwards(listWidget.getClass(), "getX");
                if (getX != null) { getX.setAccessible(true); listX = (int) getX.invoke(listWidget); }
            } catch(Exception e) {}

            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);
                int rowTop = (int) getRowTop.invoke(listWidget, i);
                int cbX    = listX + CB_MARGIN;
                int cbY    = rowTop + (ROW_H - CB_SIZE) / 2;

                boolean checked = quickAssignChecked.contains(getLevelIdSafe(entry));
                boolean hovered = mouseX >= cbX && mouseX < cbX + CB_SIZE && mouseY >= cbY && mouseY < cbY + CB_SIZE;

                int borderCol = hovered ? 0xFF99BBFF : 0xFF888888;
                gfx.fill(cbX, cbY, cbX + CB_SIZE, cbY + CB_SIZE, borderCol);
                gfx.fill(cbX + 1, cbY + 1, cbX + CB_SIZE - 1, cbY + CB_SIZE - 1, checked ? 0xFF44AA44 : 0xFF1A1A1A);

                if (checked) {
                    gfx.fill(cbX + 2, cbY + 5, cbX + 4, cbY + CB_SIZE - 1, 0xFFFFFFFF);
                    gfx.fill(cbX + 3, cbY + 2, cbX + CB_SIZE - 1, cbY + 5, 0xFFFFFFFF);
                }
            }
        } catch(Exception e) {}
    }

    public boolean onMouseClick(Screen s, MouseButtonEvent event) {
        if (quickAssignMode) return handleQuickAssignClick(event);
        if (slideProgress <= 0f) return true;

        float ease   = easeInOut(slideProgress);
        int   panelX = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs   = TabConfig.getInstance().getWorldTabs();
        double         mouseX = event.x();
        double         mouseY = event.y();

        for (int i = 0; i < tabs.size(); i++) {
            int tabX = panelX + PANEL_PAD;
            int tabY = panelTop + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tabW = PANEL_WIDTH - PANEL_PAD * 2;

            if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                TabEntry clicked = tabs.get(i);
                if ((event.modifiers() & GLFW.GLFW_MOD_ALT) != 0 && !clicked.isLocked() && "all".equals(activeTabId)) {
                    enterQuickAssign(clicked.getId());
                    return false;
                }
                activeTabId = clicked.getId();
                WorldTabSessionState.setActiveTabId(activeTabId);
                panelOpen   = false;
                clickCount  = 0;
                refreshToggleLabel();
                applyTabFilter(activeTabId);
                return false;
            }
        }
        return true;
    }

    private boolean handleQuickAssignClick(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();

        Object listWidget = getListWidget(screen);
        if (listWidget == null) return true;

        try {
            Method childrenMethod = getMethodUpwards(listWidget.getClass(), "children");
            if (childrenMethod == null) return true;
            List<?> children = (List<?>) childrenMethod.invoke(listWidget);

            Method getRowTop = getMethodUpwards(listWidget.getClass(), "getRowTop", int.class);
            if (getRowTop == null) return true;
            getRowTop.setAccessible(true);

            int listX = 10;
            try {
                Method getX = getMethodUpwards(listWidget.getClass(), "getX");
                if (getX != null) { getX.setAccessible(true); listX = (int) getX.invoke(listWidget); }
            } catch(Exception e) {}

            for (int i = 0; i < children.size(); i++) {
                Object entry = children.get(i);
                int rowTop = (int) getRowTop.invoke(listWidget, i);
                int cbX    = listX + CB_MARGIN;
                int cbY    = rowTop + (ROW_H - CB_SIZE) / 2;

                if (mouseX >= cbX && mouseX < cbX + CB_SIZE && mouseY >= cbY && mouseY < cbY + CB_SIZE) {
                    String key = getLevelIdSafe(entry);
                    if (quickAssignChecked.contains(key)) quickAssignChecked.remove(key);
                    else quickAssignChecked.add(key);
                    return false;
                }
            }
        } catch(Exception e) {}
        return true;
    }

    private void enterQuickAssign(String tabId) {
        quickAssignMode  = true;
        quickAssignTabId = tabId;
        quickAssignChecked.clear();
        panelOpen = false;
        slideProgress = 0f;
        
        applyTabFilter("all"); 
        
        if (cachedFullList != null) {
            for (Object entry : cachedFullList) {
                if (TabConfig.getInstance().worldInTab(getLevelIdSafe(entry), tabId)) {
                    quickAssignChecked.add(getLevelIdSafe(entry));
                }
            }
        }
        
        if (deselectAllButton != null) deselectAllButton.visible = true;
        refreshToggleLabel();
    }

    private void commitQuickAssign() {
        if (quickAssignTabId != null && cachedFullList != null) {
            for (Object entry : cachedFullList) {
                String key = getLevelIdSafe(entry);
                if (quickAssignChecked.contains(key)) {
                    TabConfig.getInstance().assignWorld(key, quickAssignTabId, false);
                } else {
                    TabConfig.getInstance().unassignWorld(key, quickAssignTabId, false);
                }
            }
            TabConfig.getInstance().save();
        }
        quickAssignMode    = false;
        quickAssignTabId   = null;
        quickAssignChecked.clear();
        
        if (deselectAllButton != null) deselectAllButton.visible = false;
        refreshToggleLabel();
        applyTabFilter(activeTabId);
    }

    @SuppressWarnings("unchecked")
    public void applyTabFilter(String tabId) {
        try {
            Object listWidget = getListWidget(screen);
            if (listWidget == null) return;

            Method childrenMethod = getMethodUpwards(listWidget.getClass(), "children");
            if (childrenMethod == null) return;
            List<?> currentChildren = (List<?>) childrenMethod.invoke(listWidget);
            
            if (cachedFullList == null || currentChildren.size() > cachedFullList.size()) {
                cachedFullList = new ArrayList<>(currentChildren);
            }

            List<Object> filtered = new ArrayList<>();
            for (Object entry : cachedFullList) {
                String id = getLevelIdSafe(entry);
                if ("all".equals(tabId) || TabConfig.getInstance().worldInTab(id, tabId)) {
                    filtered.add(entry);
                }
            }

            if (TabConfig.getInstance().getWorldSortingType() == TabConfig.WorldSortingType.ALPHABETICAL) {
                filtered.sort((a, b) -> getLevelNameSafe(a).compareToIgnoreCase(getLevelNameSafe(b)));
            }

            // ICON FIX: Modify children field directly instead of replaceEntries.
            Field childrenField = getFieldUpwards(listWidget.getClass(), "children");
            if (childrenField != null && List.class.isAssignableFrom(childrenField.getType())) {
                childrenField.setAccessible(true);
                List<Object> innerList = (List<Object>) childrenField.get(listWidget);
                innerList.clear(); 
                innerList.addAll(filtered);

                // Reset Scroll Position so out-of-bounds doesn't freeze interactions
                Method setScrollAmount = getMethodUpwards(listWidget.getClass(), "setScrollAmount", double.class);
                if (setScrollAmount != null) {
                    setScrollAmount.invoke(listWidget, 0.0);
                }
            } else {
                // Failsafe fallback 
                Method replaceMethod = getMethodUpwards(listWidget.getClass(), "replaceEntries", Collection.class);
                if (replaceMethod != null) {
                    replaceMethod.setAccessible(true);
                    replaceMethod.invoke(listWidget, filtered);
                }
            }
        } catch (Exception e) {}
    }

    // Static Reflection Helpers Hardened 
    private static Object getListWidget(Screen s) {
        try {
            for (Field f : s.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("WorldSelectionList")) {
                    f.setAccessible(true);
                    return f.get(s);
                }
            }
        } catch(Exception e) {}
        return null;
    }

    private static Field getFieldUpwards(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        return null;
    }

    public static String getLevelIdSafe(Object entry) {
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("LevelSummary")) {
                    f.setAccessible(true);
                    Object summary = f.get(entry);
                    String[] methodNames = {"getLevelId", "levelId", "getDirName", "getName", "getId"};
                    for (String mName : methodNames) {
                        try {
                            Method m = summary.getClass().getMethod(mName);
                            if (m.getReturnType() == String.class) {
                                return (String) m.invoke(summary);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
        return "unknown_" + System.identityHashCode(entry); // Failsafe fallback to avoid "check one checks all" visual bug
    }

    public static String getLevelNameSafe(Object entry) {
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("LevelSummary")) {
                    f.setAccessible(true);
                    Object summary = f.get(entry);
                    String[] methodNames = {"getLevelName", "levelName", "getDisplayName", "getCustomName", "getName"};
                    for (String mName : methodNames) {
                        try {
                            Method m = summary.getClass().getMethod(mName);
                            if (m.getReturnType() == String.class) {
                                return (String) m.invoke(summary);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
        return "";
    }

    private static Method getMethodUpwards(Class<?> c, String name, Class<?>... params) {
        while (c != null && c != Object.class) {
            try { return c.getDeclaredMethod(name, params); } 
            catch (NoSuchMethodException e) { c = c.getSuperclass(); }
        }
        return null;
    }

    private void refreshToggleLabel() {
        if (toggleButton == null) return;
        if (quickAssignMode) { toggleButton.setMessage(Component.literal("Done \u2713")); return; }
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        String label = tabs.stream().filter(t -> t.getId().equals(activeTabId)).map(TabEntry::getName).findFirst().orElse("All");
        String arrow = panelOpen ? " \u2191" : " \u2193";
        toggleButton.setMessage(Component.literal(label + arrow));
    }

    private static float easeInOut(float t) { return t * t * (3f - 2f * t); }
    public String getActiveTabId() { return activeTabId; }
}