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
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TabDropdownController {

    private static final int  PANEL_WIDTH       = 120;
    private static final int  PANEL_PAD         = 5;
    private static final int  TAB_HEIGHT        = 20;
    private static final int  TAB_GAP           = 3;
    private static final int  EASTER_EGG_CLICKS = 10;
    private static final int  CB_SIZE           = 10;
    private static final int  CB_MARGIN         = 4;   
    private static final int  SERVER_ROW_H      = 36;  

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

    public TabDropdownController(Screen screen) {
        this.screen      = screen;
        this.activeTabId = TabSessionState.getActiveTabId();
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
                    if (!TabConfig.getInstance().isDropdownEnabled()) return;
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
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        if (tabs.isEmpty()) return;

        int currentIdx = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(activeTabId)) { currentIdx = i; break; }
        }

        int newIdx  = Math.floorMod(currentIdx + delta, tabs.size());
        activeTabId = tabs.get(newIdx).getId();
        TabSessionState.setActiveTabId(activeTabId);
        clickCount  = 0;
        panelOpen   = false;
        refreshToggleLabel();
        applyTabFilter((JoinMultiplayerScreen) screen, activeTabId);
    }

    public void onRender(Screen s, GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (quickAssignMode) {
            renderQuickAssignOverlay(gfx, mouseX, mouseY);
            return; 
        }

        float speed = TabConfig.getInstance().getTransitionSpeed().value;
        slideProgress = panelOpen ? Math.min(1f, slideProgress + speed) : Math.max(0f, slideProgress - speed);
        if (slideProgress <= 0f) return;
        if (!TabConfig.getInstance().isDropdownEnabled()) { panelOpen = false; slideProgress = 0f; return; }

        float ease   = easeInOut(slideProgress);
        int   panelX = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs    = TabConfig.getInstance().getTabs();
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

            int bgColor = isActive  ? 0xFF3A5C3A : isHovered ? 0xFF2E3A58 : 0xFF222222;
            gfx.fill(tabX, tabY, tabX + tabW, tabY + TAB_HEIGHT, bgColor);

            int borderC = isActive ? 0xFF66BB66 : 0xFF404040;
            gfx.fill(tabX, tabY, tabX + tabW, tabY + 1, borderC);
            gfx.fill(tabX, tabY + TAB_HEIGHT - 1, tabX + tabW, tabY + TAB_HEIGHT, borderC);
            gfx.fill(tabX, tabY, tabX + 1, tabY + TAB_HEIGHT, borderC);
            gfx.fill(tabX + tabW - 1, tabY, tabX + tabW, tabY + TAB_HEIGHT, borderC);

            gfx.text(Minecraft.getInstance().font, tab.getName(), tabX + 6, tabY + (TAB_HEIGHT - 8) / 2, isActive ? 0xFF000000 | 0xFFFFFF : 0xFF000000 | 0xAAAAAA, false);

            if (isHovered && !isActive && !tab.isLocked() && "all".equals(activeTabId)) {
                gfx.text(Minecraft.getInstance().font, "[Alt]", tabX + tabW - 28, tabY + (TAB_HEIGHT - 8) / 2, 0xFF000000 | 0x777777, false);
            }
        }
    }

    private void renderQuickAssignOverlay(GuiGraphics gfx, int mouseX, int mouseY) {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList            sl  = jms.servers;
        ServerSelectionList   ssl = jms.serverSelectionList;
        if (sl == null || ssl == null) return;

        int count   = sl.size();
        int listX   = ssl.getX();

        TabEntry target = TabConfig.getInstance().getTabs().stream().filter(t -> t.getId().equals(quickAssignTabId)).findFirst().orElse(null);
        String header = "Quick Assign \u2192 " + (target != null ? target.getName() : "?");
        gfx.text(Minecraft.getInstance().font, header, 4, 30, 0xFF000000 | 0xFFDD44, false);

        for (int i = 0; i < count; i++) {
            ServerData sd = sl.get(i);
            if (sd == null) continue;

            int rowTop = ((AbstractSelectionList<?>) ssl).getRowTop(i);
            int cbX    = listX + CB_MARGIN;
            int cbY    = rowTop + (SERVER_ROW_H - CB_SIZE) / 2;

            boolean checked = quickAssignChecked.contains(normalise(sd.ip));
            boolean hovered = mouseX >= cbX && mouseX < cbX + CB_SIZE && mouseY >= cbY && mouseY < cbY + CB_SIZE;

            int borderCol = hovered ? 0xFF99BBFF : 0xFF888888;
            gfx.fill(cbX, cbY, cbX + CB_SIZE, cbY + CB_SIZE, borderCol);
            gfx.fill(cbX + 1, cbY + 1, cbX + CB_SIZE - 1, cbY + CB_SIZE - 1, checked ? 0xFF44AA44 : 0xFF1A1A1A);

            if (checked) {
                gfx.fill(cbX + 2, cbY + 5, cbX + 4, cbY + CB_SIZE - 1, 0xFFFFFFFF);
                gfx.fill(cbX + 3, cbY + 2, cbX + CB_SIZE - 1, cbY + 5, 0xFFFFFFFF);
            }
        }
    }

    public boolean onMouseClick(Screen s, MouseButtonEvent event) {
        if (quickAssignMode) return handleQuickAssignClick(event);
        if (slideProgress <= 0f) return true;

        float ease   = easeInOut(slideProgress);
        int   panelX = Math.round((ease - 1f) * PANEL_WIDTH);
        int   panelTop = 28;

        List<TabEntry> tabs   = TabConfig.getInstance().getTabs();
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
                TabSessionState.setActiveTabId(activeTabId);
                panelOpen   = false;
                clickCount  = 0;
                refreshToggleLabel();
                applyTabFilter((JoinMultiplayerScreen) screen, activeTabId);
                return false;
            }
        }
        return true;
    }

    private boolean handleQuickAssignClick(MouseButtonEvent event) {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList            sl  = jms.servers;
        ServerSelectionList   ssl = jms.serverSelectionList;
        if (sl == null || ssl == null) return true;

        double mouseX = event.x();
        double mouseY = event.y();
        int    listX  = ssl.getX();
        int    count  = sl.size();

        for (int i = 0; i < count; i++) {
            ServerData sd = sl.get(i);
            if (sd == null) continue;

            int rowTop = ((AbstractSelectionList<?>) ssl).getRowTop(i);
            int cbX    = listX + CB_MARGIN;
            int cbY    = rowTop + (SERVER_ROW_H - CB_SIZE) / 2;

            if (mouseX >= cbX && mouseX < cbX + CB_SIZE && mouseY >= cbY && mouseY < cbY + CB_SIZE) {
                String key = normalise(sd.ip);
                if (quickAssignChecked.contains(key)) quickAssignChecked.remove(key);
                else quickAssignChecked.add(key);
                return false; 
            }
        }
        return true; 
    }

    private void enterQuickAssign(String tabId) {
        quickAssignMode  = true;
        quickAssignTabId = tabId;
        quickAssignChecked.clear();
        panelOpen = false;
        slideProgress = 0f;

        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList sl = jms.servers;
        if (sl != null) {
            sl.load();
            for (int i = 0; i < sl.size(); i++) {
                ServerData sd = sl.get(i);
                if (sd != null && TabConfig.getInstance().serverInTab(sd.ip, tabId)) {
                    quickAssignChecked.add(normalise(sd.ip));
                }
            }
            jms.serverSelectionList.updateOnlineServers(sl);
        }
        
        if (deselectAllButton != null) deselectAllButton.visible = true;
        refreshToggleLabel();
    }

    private void commitQuickAssign() {
        JoinMultiplayerScreen jms = (JoinMultiplayerScreen) screen;
        ServerList sl = jms.servers;
        if (sl != null && quickAssignTabId != null) {
            for (int i = 0; i < sl.size(); i++) {
                ServerData sd = sl.get(i);
                if (sd == null) continue;
                String key = normalise(sd.ip);
                if (quickAssignChecked.contains(key)) TabConfig.getInstance().assignServer(sd.ip, quickAssignTabId, false);
                else TabConfig.getInstance().unassignServer(sd.ip, quickAssignTabId, false);
            }
            TabConfig.getInstance().save(); 
        }

        quickAssignMode    = false;
        quickAssignTabId   = null;
        quickAssignChecked.clear();
        if (deselectAllButton != null) deselectAllButton.visible = false;
        
        refreshToggleLabel();
        applyTabFilter(jms, activeTabId);
    }

    public static void applyTabFilter(JoinMultiplayerScreen jms, String tabId) {
        try {
            ServerList          servers       = jms.servers;
            ServerSelectionList selectionList = jms.serverSelectionList;

            if (servers == null || selectionList == null) return;
            servers.load();

            List<ServerData> originalList = getInternalList(servers);
            if (originalList != null) {
                List<ServerData> filteredList = new ArrayList<>(originalList);

                if (!"all".equals(tabId)) {
                    filteredList.removeIf(sd -> !TabConfig.getInstance().serverInTab(sd.ip, tabId));
                }

                TabConfig.SortingType sortType = TabConfig.getInstance().getSortingType();
                if (sortType != TabConfig.SortingType.NONE) {
                    filteredList.sort((a, b) -> {
                        switch (sortType) {
                            case ALPHABETICAL:
                                String nameA = a.name == null ? "" : a.name.toLowerCase(Locale.ROOT);
                                String nameB = b.name == null ? "" : b.name.toLowerCase(Locale.ROOT);
                                return nameA.compareTo(nameB);
                            case PING:
                                long pingA = getPingSafe(a);
                                long pingB = getPingSafe(b);
                                if (pingA < 0) pingA = Long.MAX_VALUE;
                                if (pingB < 0) pingB = Long.MAX_VALUE;
                                return Long.compare(pingA, pingB);
                            case PLAYER_COUNT:
                                int popA = getPlayerCountSafe(a);
                                int popB = getPlayerCountSafe(b);
                                return Integer.compare(popB, popA); 
                            default:
                                return 0;
                        }
                    });
                }

                setInternalList(servers, filteredList);
                selectionList.updateOnlineServers(servers);
                setInternalList(servers, originalList);
            } else {
                selectionList.updateOnlineServers(servers);
            }
        } catch (Exception e) {}
    }

    private static long getPingSafe(ServerData sd) {
        try {
            for (Field f : sd.getClass().getFields()) {
                if (f.getName().contains("ping") && f.getType() == long.class) return f.getLong(sd);
            }
        } catch (Exception e) {}
        return -1;
    }

    private static int getPlayerCountSafe(ServerData sd) {
        try {
            for (Field f : sd.getClass().getFields()) {
                Object val = f.get(sd);
                if (val != null) {
                    for (java.lang.reflect.Method m : val.getClass().getMethods()) {
                        if (m.getName().equals("online") && m.getReturnType() == int.class) return (int) m.invoke(val);
                    }
                }
            }
        } catch (Exception e) {}
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static List<ServerData> getInternalList(ServerList serverList) {
        Class<?> cls = serverList.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    try { f.setAccessible(true); return (List<ServerData>) f.get(serverList); } catch (Exception e) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void setInternalList(ServerList serverList, List<ServerData> newList) {
        Class<?> cls = serverList.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    try { f.setAccessible(true); f.set(serverList, newList); return; } catch (Exception e) {}
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private void refreshToggleLabel() {
        if (toggleButton == null) return;
        if (quickAssignMode) { toggleButton.setMessage(Component.literal("Done \u2713")); return; }
        List<TabEntry> tabs = TabConfig.getInstance().getTabs();
        String label = tabs.stream().filter(t -> t.getId().equals(activeTabId)).map(TabEntry::getName).findFirst().orElse("All");
        String arrow = panelOpen ? " \u2191" : " \u2193";
        toggleButton.setMessage(Component.literal(label + arrow));
    }

    private static float easeInOut(float t) { return t * t * (3f - 2f * t); }
    private static String normalise(String ip) { return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT); }
    public String getActiveTabId() { return activeTabId; }
    public boolean isQuickAssignMode() { return quickAssignMode; }
}