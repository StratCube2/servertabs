package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import com.servertabs.WorldTabSessionState;
import com.servertabs.ServerTabsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manages the sliding world-tab dropdown panel on SelectWorldScreen.
 *
 * Features:
 *  - Slide-in tab panel with click-to-filter
 *  - Alt+W / Alt+S keyboard tab switching (via switchTab())
 */
public class WorldTabsDropdownController {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    private static final int PANEL_WIDTH = 120;
    private static final int PANEL_PAD   = 5;
    private static final int TAB_HEIGHT  = 20;
    private static final int TAB_GAP     = 3;

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private final Screen screen;
    private boolean panelOpen     = false;
    private float   slideProgress = 0f;
    private String  activeTabId;

    private Button toggleButton;

    // -----------------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------------

    public WorldTabsDropdownController(Screen screen) {
        this.screen      = screen;
        this.activeTabId = WorldTabSessionState.getActiveTabId();
    }

    // -----------------------------------------------------------------------
    //  Public API — toggle button
    // -----------------------------------------------------------------------

    /**
     * Called on every AFTER_INIT.  Resets panel state so stale open/progress
     * values can never block clicks after returning from a sub-screen.
     */
    public Button createToggleButton() {
        panelOpen     = false;
        slideProgress = 0f;

        toggleButton = Button.builder(
                Component.literal("Tabs \u2193"),
                btn -> {
                    if (!TabConfig.getInstance().isWorldTabsEnabled()) return;
                    if (!TabConfig.getInstance().isWorldDropdownEnabled()) return;
                    panelOpen = !panelOpen;
                    refreshToggleLabel();
                })
                .bounds(4, 4, 60, 20)
                .build();
        refreshToggleLabel();
        return toggleButton;
    }

    // -----------------------------------------------------------------------
    //  Public API — Alt+W/S keyboard tab switching
    // -----------------------------------------------------------------------

    public void switchTab(int delta) {
        if (!TabConfig.getInstance().isWorldTabsEnabled()) return;

        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        if (tabs.isEmpty()) return;

        int currentIdx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(activeTabId)) { currentIdx = i; break; }
        }

        int newIdx  = Math.floorMod(currentIdx + delta, tabs.size());
        activeTabId = tabs.get(newIdx).getId();
        WorldTabSessionState.setActiveTabId(activeTabId);
        panelOpen   = false;
        refreshToggleLabel();
        applyTabFilter((SelectWorldScreen) screen, activeTabId);
    }

    public String getActiveTabId() { return activeTabId; }

    // -----------------------------------------------------------------------
    //  Render
    // -----------------------------------------------------------------------

    public void onRender(Screen s, GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (!TabConfig.getInstance().isWorldTabsEnabled()) return;

        float speed = TabConfig.getInstance().getWorldTransitionSpeed().value;
        slideProgress = panelOpen
                ? Math.min(1f, slideProgress + speed)
                : Math.max(0f, slideProgress - speed);

        if (slideProgress <= 0f) return;

        if (!TabConfig.getInstance().isWorldDropdownEnabled()) {
            panelOpen = false; slideProgress = 0f;
            return;
        }

        float ease    = easeInOut(slideProgress);
        int   panelX  = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs    = TabConfig.getInstance().getWorldTabs();
        int            tabAreaH = tabs.size() * (TAB_HEIGHT + TAB_GAP) - TAB_GAP;
        int            panelH  = PANEL_PAD * 2 + tabAreaH;

        // Panel background + borders
        gfx.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + panelH, 0xC8101010);
        int border = 0xFF555555;
        gfx.fill(panelX,                   panelTop,              panelX + PANEL_WIDTH, panelTop + 1,      border);
        gfx.fill(panelX,                   panelTop + panelH - 1, panelX + PANEL_WIDTH, panelTop + panelH, border);
        gfx.fill(panelX + PANEL_WIDTH - 1, panelTop,              panelX + PANEL_WIDTH, panelTop + panelH, border);

        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab  = tabs.get(i);
            int tabX = panelX + PANEL_PAD;
            int tabY = panelTop + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tabW = PANEL_WIDTH - PANEL_PAD * 2;

            boolean isActive  = tab.getId().equals(activeTabId);
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabW
                             && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bgColor = isActive  ? 0xFF3A5C3A
                        : isHovered ? 0xFF2E3A58
                        :             0xFF222222;
            gfx.fill(tabX, tabY, tabX + tabW, tabY + TAB_HEIGHT, bgColor);

            int borderC = isActive ? 0xFF66BB66 : 0xFF404040;
            gfx.fill(tabX,            tabY,                  tabX + tabW, tabY + 1,              borderC);
            gfx.fill(tabX,            tabY + TAB_HEIGHT - 1, tabX + tabW, tabY + TAB_HEIGHT,     borderC);
            gfx.fill(tabX,            tabY,                  tabX + 1,    tabY + TAB_HEIGHT,     borderC);
            gfx.fill(tabX + tabW - 1, tabY,                  tabX + tabW, tabY + TAB_HEIGHT,     borderC);

            gfx.drawString(
                    Minecraft.getInstance().font,
                    tab.getName(),
                    tabX + 6,
                    tabY + (TAB_HEIGHT - 8) / 2,
                    isActive ? 0xFFFFFFFF : 0xFFAAAAAA,
                    false);
        }
    }

    // -----------------------------------------------------------------------
    //  Mouse click
    // -----------------------------------------------------------------------

    public boolean onMouseClick(Screen s, MouseButtonEvent event) {
        if (!TabConfig.getInstance().isWorldTabsEnabled()) return true;
        if (slideProgress <= 0f) return true;

        float ease    = easeInOut(slideProgress);
        int   panelX  = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs   = TabConfig.getInstance().getWorldTabs();
        double         mouseX = event.x();
        double         mouseY = event.y();

        for (int i = 0; i < tabs.size(); i++) {
            int tabX = panelX + PANEL_PAD;
            int tabY = panelTop + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tabW = PANEL_WIDTH - PANEL_PAD * 2;

            if (mouseX >= tabX && mouseX < tabX + tabW
             && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {

                activeTabId = tabs.get(i).getId();
                WorldTabSessionState.setActiveTabId(activeTabId);
                panelOpen   = false;
                refreshToggleLabel();
                applyTabFilter((SelectWorldScreen) screen, activeTabId);
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Core filtering logic
    // -----------------------------------------------------------------------

    /**
     * Filters the SelectWorldScreen's world list to only show worlds in the given tab.
     * Uses reflection to access the WorldSelectionList and its internal entry list.
     */
    public static void applyTabFilter(SelectWorldScreen sws, String tabId) {
        try {
            WorldSelectionList worldList = getWorldList(sws);
            if (worldList == null) {
                ServerTabsMod.LOGGER.warn("[ServerTabs] applyTabFilter (world): could not get WorldSelectionList");
                return;
            }

            // Reload the full list first via reflection (method name may vary by MC version)
            tryRefreshList(worldList);

            if (!"all".equals(tabId)) {
                // Get the internal children list from AbstractSelectionList via reflection
                List<?> children = getChildrenList(worldList);
                if (children != null) {
                    children.removeIf(entry -> {
                        String levelId = getLevelId(entry);
                        return levelId != null && !TabConfig.getInstance().worldInTab(levelId, tabId);
                    });
                }
            }

        } catch (Exception e) {
            ServerTabsMod.LOGGER.warn("[ServerTabs] applyTabFilter (world) failed", e);
        }
    }

    /** Empty-string supplier used as the search filter when reloading the world list. */
    private static final Supplier<String> EMPTY_FILTER = () -> "";

    /** Attempts to refresh/reload the WorldSelectionList using reflection. */
    private static void tryRefreshList(WorldSelectionList list) {
        Class<?> cls = list.getClass();
        while (cls != null && cls != Object.class) {
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (m.getName().contains("refresh") || m.getName().contains("reload")) {
                    try {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 0) {
                            m.invoke(list);
                            return;
                        } else if (m.getParameterCount() == 2
                                && m.getParameterTypes()[0] == Supplier.class
                                && m.getParameterTypes()[1] == boolean.class) {
                            m.invoke(list, EMPTY_FILTER, false);
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
    }

    // -----------------------------------------------------------------------
    //  Reflection helpers
    // -----------------------------------------------------------------------

    /** Finds the WorldSelectionList field on SelectWorldScreen via reflection. */
    private static WorldSelectionList getWorldList(SelectWorldScreen sws) {
        Class<?> cls = sws.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (WorldSelectionList.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (WorldSelectionList) f.get(sws);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /** Gets the mutable internal children list from an AbstractSelectionList via reflection. */
    @SuppressWarnings("unchecked")
    private static List<Object> getChildrenList(WorldSelectionList list) {
        Class<?> cls = list.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(list);
                        if (value instanceof List) {
                            // Make sure it's mutable (not an unmodifiable wrapper)
                            List<Object> mutable = new ArrayList<>((List<Object>) value);
                            f.set(list, mutable);
                            return mutable;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /** Extracts the level ID from a WorldSelectionList.Entry via reflection. */
    private static String getLevelId(Object entry) {
        if (entry == null) return null;
        Class<?> cls = entry.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (LevelSummary.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        LevelSummary summary = (LevelSummary) f.get(entry);
                        return summary != null ? summary.getLevelId() : null;
                    } catch (Exception ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private void refreshToggleLabel() {
        if (toggleButton == null) return;
        if (!TabConfig.getInstance().isWorldTabsEnabled()) {
            toggleButton.setMessage(Component.literal("Tabs \u2193"));
            return;
        }
        List<TabEntry> tabs = TabConfig.getInstance().getWorldTabs();
        String label = tabs.stream()
                .filter(t -> t.getId().equals(activeTabId))
                .map(TabEntry::getName)
                .findFirst()
                .orElse("All");
        String arrow = panelOpen ? " \u2191" : " \u2193";
        toggleButton.setMessage(Component.literal(label + arrow));
    }

    private static float easeInOut(float t) {
        return t * t * (3f - 2f * t);
    }
}
