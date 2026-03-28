package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import com.servertabs.TabSessionState;
import com.servertabs.ServerTabsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Manages the sliding tab dropdown panel on JoinMultiplayerScreen.
 *
 * On tab click: updates TabSessionState, reloads the full ServerList from disk,
 * filters it in-memory for the chosen tab, then calls updateOnlineServers()
 * directly on the live ServerSelectionList widget.
 *
 * No screen reinit — no flash, no duplicate listeners, no WeakHashMap churn.
 */
public class TabDropdownController {

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

    public TabDropdownController(Screen screen) {
        this.screen      = screen;
        this.activeTabId = TabSessionState.getActiveTabId();
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    public Button createToggleButton() {
        toggleButton = Button.builder(
                Component.literal("Tabs \u2193"),
                btn -> {
                    if (!TabConfig.getInstance().isDropdownEnabled()) return;
                    panelOpen = !panelOpen;
                    refreshToggleLabel();
                })
                .bounds(4, 4, 60, 20)
                .build();
        refreshToggleLabel();
        return toggleButton;
    }

    public void onRender(Screen s, GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        float speed = TabConfig.getInstance().getTransitionSpeed().value;
        if (panelOpen) {
            slideProgress = Math.min(1f, slideProgress + speed);
        } else {
            slideProgress = Math.max(0f, slideProgress - speed);
        }

        if (slideProgress <= 0f) return;

        if (!TabConfig.getInstance().isDropdownEnabled()) {
            panelOpen = false; slideProgress = 0f;
            return;
        }

        float t    = slideProgress;
        float ease = t * t * (3f - 2f * t);
        int panelX   = Math.round((ease - 1f) * PANEL_WIDTH);
        int panelTop = 28;

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int tabAreaH = tabs.size() * (TAB_HEIGHT + TAB_GAP) - TAB_GAP;
        int panelH   = PANEL_PAD * 2 + tabAreaH;

        gfx.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + panelH, 0xC8101010);
        int border = 0xFF555555;
        gfx.fill(panelX,                   panelTop,              panelX + PANEL_WIDTH, panelTop + 1,           border);
        gfx.fill(panelX,                   panelTop + panelH - 1, panelX + PANEL_WIDTH, panelTop + panelH,      border);
        gfx.fill(panelX + PANEL_WIDTH - 1, panelTop,              panelX + PANEL_WIDTH, panelTop + panelH,      border);

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
                    isActive ? 0xFF000000 | 0xFFFFFF : 0xFF000000 | 0xAAAAAA,
                    false);
        }
    }

    public boolean onMouseClick(Screen s, MouseButtonEvent event) {
        if (slideProgress <= 0f) return true;

        float t    = slideProgress;
        float ease = t * t * (3f - 2f * t);
        int panelX   = Math.round((ease - 1f) * PANEL_WIDTH);
        int panelTop = 28;

        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        double mouseX = event.x();
        double mouseY = event.y();

        for (int i = 0; i < tabs.size(); i++) {
            int tabX = panelX + PANEL_PAD;
            int tabY = panelTop + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tabW = PANEL_WIDTH - PANEL_PAD * 2;

            if (mouseX >= tabX && mouseX < tabX + tabW
             && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {

                activeTabId = tabs.get(i).getId();
                TabSessionState.setActiveTabId(activeTabId);
                panelOpen = false;
                refreshToggleLabel();

                // Apply the tab filter directly on the live widget.
                // No screen reinit needed.
                applyTabFilter((JoinMultiplayerScreen) screen, activeTabId);
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Core filtering logic
    // -----------------------------------------------------------------------

    /**
     * Reloads the full ServerList from disk, optionally filters it in-memory,
     * then calls updateOnlineServers() on the live ServerSelectionList widget.
     *
     * Vanilla's updateOnlineServers() does clearEntries() + addEntry() for each
     * ServerData — it constructs the OnlineServerEntry objects itself, so we
     * never need to touch that class directly.
     *
     * Idempotent: safe to call repeatedly. Each call starts from a clean
     * disk-loaded state, so no "ghost" servers can accumulate.
     */
    public static void applyTabFilter(JoinMultiplayerScreen jms, String tabId) {
        try {
            // Fields widened by servertabs.accesswidener
            ServerList          servers       = jms.servers;
            ServerSelectionList selectionList = jms.serverSelectionList;

            // Always reload from disk first — this restores servers hidden by
            // any previous filter call, guaranteeing a clean starting state.
            servers.load();

            // For non-"all" tabs, remove servers not assigned to this tab.
            if (!"all".equals(tabId)) {
                List<ServerData> list = getInternalList(servers);
                if (list != null) {
                    list.removeIf(sd ->
                            !TabConfig.getInstance().serverInTab(sd.ip, tabId));
                }
            }

            // Rebuild the visual widget from the (now filtered) ServerList.
            // Vanilla handles clearEntries() + entry construction internally.
            selectionList.updateOnlineServers(servers);

        } catch (Exception e) {
            ServerTabsMod.LOGGER.warn("[ServerTabs] applyTabFilter failed", e);
        }
    }

    /**
     * Reflects into ServerList to get its internal List<ServerData>.
     * Called once per tab switch — reflection cost is negligible.
     */
    @SuppressWarnings("unchecked")
    private static List<ServerData> getInternalList(ServerList serverList) {
        Class<?> cls = serverList.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (List<ServerData>) f.get(serverList);
                    } catch (Exception e) {
                        ServerTabsMod.LOGGER.warn("[ServerTabs] Could not access ServerList internal list", e);
                        return null;
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        ServerTabsMod.LOGGER.warn("[ServerTabs] Could not find List field inside ServerList");
        return null;
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private void refreshToggleLabel() {
        if (toggleButton == null) return;
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        String label = tabs.stream()
                .filter(t -> t.getId().equals(activeTabId))
                .map(TabEntry::getName)
                .findFirst()
                .orElse("All");
        String arrow = panelOpen ? " \u2191" : " \u2193";
        toggleButton.setMessage(Component.literal(label + arrow));
    }

    public String getActiveTabId() { return activeTabId; }
}
