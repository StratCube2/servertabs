package com.servertabs;

import com.servertabs.gui.AssignTabScreen;
import com.servertabs.gui.AssignWorldScreen;
import com.servertabs.gui.TabDropdownController;
import com.servertabs.gui.WorldTabsDropdownController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public class ServerTabsClient implements ClientModInitializer {

    /**
     * Tracks controllers by JoinMultiplayerScreen INSTANCE.
     * WeakHashMap: when the screen is GC'd the entry is removed automatically.
     */
    private static final WeakHashMap<Screen, TabDropdownController> controllers
            = new WeakHashMap<>();

    /** Tracks world-tab controllers by SelectWorldScreen INSTANCE. */
    private static final WeakHashMap<Screen, WorldTabsDropdownController> worldControllers
            = new WeakHashMap<>();

    /**
     * Tracks which non-JMS screens have already received our injected button,
     * to prevent adding duplicates when the screen reinits.
     */
    private static final WeakHashMap<Screen, Boolean> injectedScreens
            = new WeakHashMap<>();

    /**
     * Tracks server count as of the last JMS init, so we can detect when a
     * new server was just added (Feature 3: Assign on Add).
     */
    private static int lastKnownServerCount = -1;

    /** Tracks world IDs seen on the last SelectWorldScreen open, for assign-on-add. */
    private static Set<String> lastKnownWorldIds = null;

    @Override
    public void onInitializeClient() {
        ServerTabsMod.LOGGER.info("ServerTabs client initialized!");
        TabConfig.getInstance();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

            // ----------------------------------------------------------------
            // Multiplayer server list
            // ----------------------------------------------------------------
            if (screen instanceof JoinMultiplayerScreen jms) {

                TabDropdownController controller = controllers.get(screen);
                if (controller == null) {
                    controller = new TabDropdownController(screen);
                    controllers.put(screen, controller);
                    ScreenEvents.afterRender(screen).register(controller::onRender);
                    ScreenMouseEvents.allowMouseClick(screen).register(controller::onMouseClick);

                    final TabDropdownController ctrl = controller;
                    ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                        if ((keyEvent.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                            if (keyEvent.key() == GLFW.GLFW_KEY_W) { ctrl.switchTab(-1); return false; }
                            if (keyEvent.key() == GLFW.GLFW_KEY_S) { ctrl.switchTab(+1); return false; }
                        }
                        return true;
                    });
                }

                Screens.getButtons(screen).add(controller.createToggleButton());

                // Feature: Assign on Add
                if (TabConfig.getInstance().isAssignOnAdd()) {
                    ServerList servers = jms.servers;
                    if (servers != null) {
                        servers.load();
                        int currentCount = servers.size();

                        if (lastKnownServerCount >= 0
                                && currentCount > lastKnownServerCount) {
                            ServerData newest = servers.get(currentCount - 1);
                            if (newest != null) {
                                String ip   = newest.ip   != null ? newest.ip.trim()   : "";
                                String name = newest.name != null ? newest.name.trim() : "";
                                final Screen jmsScreen = screen;
                                client.execute(() ->
                                    client.setScreen(new AssignTabScreen(jmsScreen, ip, name))
                                );
                                lastKnownServerCount = currentCount;
                                return;
                            }
                        }
                        lastKnownServerCount = currentCount;
                    }
                }

                TabDropdownController.applyTabFilter(jms, TabSessionState.getActiveTabId());
                return;
            }

            // ----------------------------------------------------------------
            // Singleplayer world list
            // ----------------------------------------------------------------
            if (screen instanceof SelectWorldScreen sws) {

                if (!TabConfig.getInstance().isWorldTabsEnabled()) return;

                WorldTabsDropdownController worldCtrl = worldControllers.get(screen);
                if (worldCtrl == null) {
                    worldCtrl = new WorldTabsDropdownController(screen);
                    worldControllers.put(screen, worldCtrl);
                    ScreenEvents.afterRender(screen).register(worldCtrl::onRender);
                    ScreenMouseEvents.allowMouseClick(screen).register(worldCtrl::onMouseClick);

                    final WorldTabsDropdownController wc = worldCtrl;
                    ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                        if ((keyEvent.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                            if (keyEvent.key() == GLFW.GLFW_KEY_W) { wc.switchTab(-1); return false; }
                            if (keyEvent.key() == GLFW.GLFW_KEY_S) { wc.switchTab(+1); return false; }
                        }
                        return true;
                    });
                }

                Screens.getButtons(screen).add(worldCtrl.createToggleButton());

                // Feature: World Assign on Add — detect newly created worlds
                if (TabConfig.getInstance().isWorldAssignOnAdd()) {
                    WorldSelectionList worldList = findWorldList(sws);
                    if (worldList != null) {
                        Set<String> currentIds = collectWorldIds(worldList);
                        if (lastKnownWorldIds != null && !currentIds.isEmpty()) {
                            // Find IDs present now but not before
                            for (String id : currentIds) {
                                if (!lastKnownWorldIds.contains(id)) {
                                    LevelSummary summary = findWorldSummary(worldList, id);
                                    if (summary != null) {
                                        lastKnownWorldIds = currentIds;
                                        final Screen swsScreen = screen;
                                        final LevelSummary s = summary;
                                        client.execute(() ->
                                            client.setScreen(new AssignWorldScreen(
                                                swsScreen, s.getLevelId(), s.getLevelName()))
                                        );
                                        WorldTabsDropdownController.applyTabFilter(sws, WorldTabSessionState.getActiveTabId());
                                        return;
                                    }
                                }
                            }
                        }
                        lastKnownWorldIds = currentIds;
                    }
                }

                WorldTabsDropdownController.applyTabFilter(sws, WorldTabSessionState.getActiveTabId());
                return;
            }

            // ----------------------------------------------------------------
            // Main menu — reset tab tracking if remember is OFF
            // ----------------------------------------------------------------
            if (screen instanceof TitleScreen) {
                if (!TabConfig.getInstance().isRememberTab()) {
                    TabSessionState.resetToDefault();
                }
                if (!TabConfig.getInstance().isWorldRememberTab()) {
                    WorldTabSessionState.resetToDefault();
                }
                lastKnownServerCount = -1;
                lastKnownWorldIds    = null;
                return;
            }

            // ----------------------------------------------------------------
            // Add/Edit server screen — inject "Assign Tab" button (once only)
            // ----------------------------------------------------------------
            if (injectedScreens.containsKey(screen)) return;

            ServerData serverData = findServerData(screen);
            if (serverData != null) {
                injectedScreens.put(screen, Boolean.TRUE);
                final ServerData sd = serverData;
                Button assignBtn = Button.builder(
                        Component.literal("Assign Tab"),
                        btn -> {
                            String ip   = sd.ip   != null ? sd.ip.trim()   : "";
                            String name = sd.name != null ? sd.name.trim() : "";
                            client.setScreen(new AssignTabScreen(screen, ip, name));
                        })
                        .bounds(scaledWidth / 2 + 4 + 105, 10, 100, 16)
                        .build();
                Screens.getButtons(screen).add(assignBtn);
            }
        });
    }

    // -----------------------------------------------------------------------
    //  Reflection helper — find ServerData on any screen (for Assign Tab btn)
    // -----------------------------------------------------------------------

    private static ServerData findServerData(Screen screen) {
        Class<?> cls = screen.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == ServerData.class) {
                    try {
                        f.setAccessible(true);
                        return (ServerData) f.get(screen);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Reflection helpers — world list
    // -----------------------------------------------------------------------

    private static WorldSelectionList findWorldList(SelectWorldScreen sws) {
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

    /** Collects all level IDs currently visible in the world list. */
    private static Set<String> collectWorldIds(WorldSelectionList list) {
        Set<String> ids = new HashSet<>();
        List<?> children = getChildrenViaReflection(list);
        if (children == null) return ids;
        for (Object entry : children) {
            LevelSummary summary = getLevelSummaryFromEntry(entry);
            if (summary != null && summary.getLevelId() != null) {
                ids.add(summary.getLevelId());
            }
        }
        return ids;
    }

    /** Finds the LevelSummary with the given level ID in the world list. */
    private static LevelSummary findWorldSummary(WorldSelectionList list, String levelId) {
        List<?> children = getChildrenViaReflection(list);
        if (children == null) return null;
        for (Object entry : children) {
            LevelSummary summary = getLevelSummaryFromEntry(entry);
            if (summary != null && levelId.equals(summary.getLevelId())) return summary;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<?> getChildrenViaReflection(Object obj) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(obj);
                        if (value instanceof List) return (List<?>) value;
                    } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static LevelSummary getLevelSummaryFromEntry(Object entry) {
        if (entry == null) return null;
        Class<?> cls = entry.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (LevelSummary.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (LevelSummary) f.get(entry);
                    } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
