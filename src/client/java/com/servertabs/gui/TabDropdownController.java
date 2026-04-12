package com.servertabs.gui;

import com.servertabs.TabConfig;
import com.servertabs.TabEntry;
import com.servertabs.TabSessionState;
import com.servertabs.ServerTabsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.util.*;

public class TabDropdownController {

    private static final int PANEL_WIDTH = 120;
    private static final int PANEL_PAD = 5;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 3;
    private static final int EASTER_EGG_CLICKS = 10;
    private static final int CB_SIZE = 10;
    private static final int CB_MARGIN = 4;
    private static final int SERVER_ROW_H = 36;

    // Cache to prevent main-thread Disk I/O
    private static List<ServerData> masterServerCache = new ArrayList<>();

    private final Screen screen;
    private boolean panelOpen = false;
    private float slideProgress = 0f;
    private String activeTabId;
    private int clickCount = 0;
    private Button toggleButton;

    private boolean quickAssignMode = false;
    private String quickAssignTabId = null;
    private Set<String> quickAssignChecked = new HashSet<>();

    public TabDropdownController(Screen screen) {
        this.screen = screen;
        this.activeTabId = TabSessionState.getActiveTabId();
    }

    public static void clearCache() {
        masterServerCache.clear();
    }

    public Button createToggleButton() {
        panelOpen = false;
        slideProgress = 0f;
        toggleButton = Button.builder(Component.literal("Tabs \u2193"), btn -> {
            if (quickAssignMode) { commitQuickAssign(); return; }
            if (!TabConfig.getInstance().isDropdownEnabled()) return;
            panelOpen = !panelOpen;
            refreshToggleLabel();
            if (++clickCount >= EASTER_EGG_CLICKS) {
                clickCount = 0;
                Minecraft.getInstance().setScreen(new EasterEggScreen(screen));
            }
        }).bounds(4, 4, 60, 20).build();
        refreshToggleLabel();
        return toggleButton;
    }

    public void switchTab(int delta) {
        if (quickAssignMode) return;
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (tabs.isEmpty()) return;
        int currentIdx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(activeTabId)) { currentIdx = i; break; }
        }
        activeTabId = tabs.get(Math.floorMod(currentIdx + delta, tabs.size())).getId();
        TabSessionState.setActiveTabId(activeTabId);
        panelOpen = false;
        refreshToggleLabel();
        applyTabFilter((JoinMultiplayerScreen) screen, activeTabId);
    }

    public void onRender(Screen s, GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (quickAssignMode) { renderQuickAssignOverlay(gfx, mouseX, mouseY); return; }
        float speed = TabConfig.getInstance().getTransitionSpeed().value;
        slideProgress = panelOpen ? Math.min(1f, slideProgress + speed) : Math.max(0f, slideProgress - speed);
        if (slideProgress <= 0f) return;

        float ease = easeInOut(slideProgress);
        int px = Math.round((ease - 1f) * PANEL_WIDTH);
        int pt = 28;
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        int ph = PANEL_PAD * 2 + tabs.size() * (TAB_HEIGHT + TAB_GAP) - TAB_GAP;

        gfx.fill(px, pt, px + PANEL_WIDTH, pt + ph, 0xC8101010);
        gfx.fill(px, pt, px + PANEL_WIDTH, pt + 1, 0xFF555555);
        gfx.fill(px, pt + ph - 1, px + PANEL_WIDTH, pt + ph, 0xFF555555);
        gfx.fill(px + PANEL_WIDTH - 1, pt, px + PANEL_WIDTH, pt + ph, 0xFF555555);

        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab = tabs.get(i);
            int tx = px + PANEL_PAD;
            int ty = pt + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
            int tw = PANEL_WIDTH - PANEL_PAD * 2;
            boolean active = tab.getId().equals(activeTabId);
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TAB_HEIGHT;
            gfx.fill(tx, ty, tx + tw, ty + TAB_HEIGHT, active ? 0xFF3A5C3A : (hovered ? 0xFF2E3A58 : 0xFF222222));
            gfx.text(Minecraft.getInstance().font, tab.getName(), tx + 6, ty + (TAB_HEIGHT - 8) / 2, active ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
    }

    private void renderQuickAssignOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList sl = jms.servers;
        ServerSelectionList ssl = jms.serverSelectionList;
        if (sl == null || ssl == null) return;
        gfx.text(Minecraft.getInstance().font, "Quick Assign Mode", 4, 28, 0xFFFFDD44, false);
        for (int i = 0; i < sl.size(); i++) {
            ServerData sd = sl.get(i);
            if (sd == null) continue;
            int rowTop = ((AbstractSelectionList<?>) ssl).getRowTop(i);
            int cbX = ssl.getX() + CB_MARGIN;
            int cbY = rowTop + (SERVER_ROW_H - CB_SIZE) / 2;
            boolean checked = quickAssignChecked.contains(normalise(sd.ip));
            gfx.fill(cbX, cbY, cbX + CB_SIZE, cbY + CB_SIZE, 0xFF888888);
            gfx.fill(cbX + 1, cbY + 1, cbX + CB_SIZE - 1, cbY + CB_SIZE - 1, checked ? 0xFF44AA44 : 0xFF1A1A1A);
        }
    }

    public boolean onMouseClick(Screen s, MouseButtonEvent event) {
        if (quickAssignMode) return handleQuickAssignClick(event);
        if (slideProgress <= 0f) return true;

        float ease = easeInOut(slideProgress);
        int px = Math.round((ease - 1f) * PANEL_WIDTH);
        int pt = 28;
        double mx = event.x(), my = event.y();

        if (mx >= px && mx < px + PANEL_WIDTH) {
            List<TabEntry> tabs = TabConfig.getInstance().getTabs();
            for (int i = 0; i < tabs.size(); i++) {
                int ty = pt + PANEL_PAD + i * (TAB_HEIGHT + TAB_GAP);
                if (my >= ty && my < ty + TAB_HEIGHT) {
                    TabEntry clicked = tabs.get(i);
                    if ((event.modifiers() & GLFW.GLFW_MOD_ALT) != 0 && !clicked.isLocked() && "all".equals(activeTabId)) {
                        enterQuickAssign(clicked.getId());
                    } else {
                        activeTabId = clicked.getId();
                        TabSessionState.setActiveTabId(activeTabId);
                        panelOpen = false;
                        refreshToggleLabel();
                        applyTabFilter((JoinMultiplayerScreen) screen, activeTabId);
                    }
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    private boolean handleQuickAssignClick(MouseButtonEvent event) {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList sl = jms.servers;
        ServerSelectionList ssl = jms.serverSelectionList;
        if (sl == null || ssl == null) return true;
        double mx = event.x(), my = event.y();
        for (int i = 0; i < sl.size(); i++) {
            ServerData sd = sl.get(i);
            if (sd == null) continue;
            int rowTop = ((AbstractSelectionList<?>) ssl).getRowTop(i);
            int cbX = ssl.getX() + CB_MARGIN, cbY = rowTop + (SERVER_ROW_H - CB_SIZE) / 2;
            if (mx >= cbX && mx < cbX + CB_SIZE && my >= cbY && my < cbY + CB_SIZE) {
                String key = normalise(sd.ip);
                if (!quickAssignChecked.remove(key)) quickAssignChecked.add(key);
                return false;
            }
        }
        return true;
    }

    private void enterQuickAssign(String tabId) {
        quickAssignMode = true; quickAssignTabId = tabId; quickAssignChecked.clear(); panelOpen = false;
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        if (jms.servers != null) {
            jms.servers.load();
            for (int i = 0; i < jms.servers.size(); i++) {
                ServerData sd = jms.servers.get(i);
                if (sd != null && TabConfig.getInstance().serverInTab(sd.ip, tabId)) quickAssignChecked.add(normalise(sd.ip));
            }
            jms.serverSelectionList.updateOnlineServers(jms.servers);
        }
        refreshToggleLabel();
    }

    private void commitQuickAssign() {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        if (jms.servers != null && quickAssignTabId != null) {
            for (int i = 0; i < jms.servers.size(); i++) {
                ServerData sd = jms.servers.get(i);
                if (sd == null) continue;
                if (quickAssignChecked.contains(normalise(sd.ip))) TabConfig.getInstance().assignServer(sd.ip, quickAssignTabId);
                else TabConfig.getInstance().unassignServer(sd.ip, quickAssignTabId);
            }
            TabConfig.getInstance().save();
        }
        quickAssignMode = false; refreshToggleLabel(); applyTabFilter(jms, activeTabId);
    }

    public static void applyTabFilter(JoinMultiplayerScreen jms, String tabId) {
        try {
            if (jms == null || jms.servers == null) return;
            List<ServerData> internal = getInternalList(jms.servers);
            if (internal == null) return;

            // Load from disk only if cache is stale/empty
            if (masterServerCache.isEmpty() || internal.size() > masterServerCache.size()) {
                jms.servers.load();
                masterServerCache = new ArrayList<>(internal);
            }

            internal.clear();
            internal.addAll(masterServerCache);
            if (!"all".equals(tabId)) internal.removeIf(sd -> !TabConfig.getInstance().serverInTab(sd.ip, tabId));
            jms.serverSelectionList.updateOnlineServers(jms.servers);
        } catch (Exception e) { ServerTabsMod.LOGGER.error("Filter failed", e); }
    }

    private static List<ServerData> getInternalList(ServerList list) {
        try {
            Field f = ServerList.class.getDeclaredField("servers");
            f.setAccessible(true);
            return (List<ServerData>) f.get(list);
        } catch (Exception e) { return null; }
    }

    private void refreshToggleLabel() {
        if (toggleButton == null) return;
        if (quickAssignMode) { toggleButton.setMessage(Component.literal("Done \u2713")); return; }
        String name = TabConfig.getInstance().getTabs().stream().filter(t -> t.getId().equals(activeTabId)).map(TabEntry::getName).findFirst().orElse("All");
        toggleButton.setMessage(Component.literal(name + (panelOpen ? " \u2191" : " \u2193")));
    }

    private static float easeInOut(float t) { return t * t * (3f - 2f * t); }
    private static String normalise(String ip) { return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT); }
}