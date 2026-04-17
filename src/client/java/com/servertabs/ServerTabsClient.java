package com.servertabs;

import com.servertabs.gui.AssignTabScreen;
import com.servertabs.gui.AssignWorldTabScreen;
import com.servertabs.gui.TabDropdownController;
import com.servertabs.gui.WorldTabDropdownController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

public class ServerTabsClient implements ClientModInitializer {

    private static final WeakHashMap<Screen, TabDropdownController> controllers = new WeakHashMap<>();
    private static final WeakHashMap<Screen, WorldTabDropdownController> worldControllers = new WeakHashMap<>();
    private static final WeakHashMap<Screen, Boolean> injectedScreens = new WeakHashMap<>();

    private static int lastKnownServerCount = -1;

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
                    
                    ScreenEvents.afterExtract(screen).register(controller::onRender);
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

                controller.setupWidgets(screen);

                if (TabConfig.getInstance().isAssignOnAdd()) {
                    ServerList servers = jms.servers;
                    if (servers != null) {
                        servers.load(); 
                        int currentCount = servers.size();
                        if (lastKnownServerCount >= 0 && currentCount > lastKnownServerCount) {
                            ServerData newest = servers.get(currentCount - 1);
                            if (newest != null) {
                                String ip   = newest.ip   != null ? newest.ip.trim()   : "";
                                String name = newest.name != null ? newest.name.trim() : "";
                                final Screen jmsScreen = screen;
                                client.execute(() -> client.setScreen(new AssignTabScreen(jmsScreen, ip, name)));
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
            // Singleplayer World List
            // ----------------------------------------------------------------
            if (screen.getClass().getSimpleName().equals("SelectWorldScreen")) {
                if (!TabConfig.getInstance().isWorldTabsEnabled()) return;

                WorldTabDropdownController controller = worldControllers.get(screen);
                if (controller == null) {
                    controller = new WorldTabDropdownController(screen);
                    worldControllers.put(screen, controller);
                    
                    ScreenEvents.afterExtract(screen).register(controller::onRender);
                    ScreenMouseEvents.allowMouseClick(screen).register(controller::onMouseClick);

                    final WorldTabDropdownController ctrl = controller;
                    ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                        if ((keyEvent.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                            if (keyEvent.key() == GLFW.GLFW_KEY_W) { ctrl.switchTab(-1); return false; }
                            if (keyEvent.key() == GLFW.GLFW_KEY_S) { ctrl.switchTab(+1); return false; }
                        }
                        return true;
                    });
                }

                controller.setupWidgets(screen);
                controller.applyTabFilter(WorldTabSessionState.getActiveTabId());
                return;
            }

            // ----------------------------------------------------------------
            // Main menu 
            // ----------------------------------------------------------------
            if (screen instanceof TitleScreen) {
                if (!TabConfig.getInstance().isRememberTab()) TabSessionState.resetToDefault();
                if (!TabConfig.getInstance().isWorldRememberTab()) WorldTabSessionState.resetToDefault();
                lastKnownServerCount = -1;
                return;
            }

            if (injectedScreens.containsKey(screen)) return;

            // ----------------------------------------------------------------
            // Add/Edit server screen injection
            // ----------------------------------------------------------------
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
                Screens.getWidgets(screen).add(assignBtn);
                return;
            }

            // ----------------------------------------------------------------
            // World Editing screen injection
            // ----------------------------------------------------------------
            if (screen.getClass().getSimpleName().equals("EditWorldScreen")) {
                if (!TabConfig.getInstance().isWorldTabsEnabled()) return;

                String[] worldData = findWorldData(screen);
                if (worldData != null) {
                    injectedScreens.put(screen, Boolean.TRUE);
                    final String wId   = worldData[0];
                    final String wName = worldData[1];
                    
                    Button assignBtn = Button.builder(
                            Component.literal("Assign Tab"),
                            btn -> {
                                client.setScreen(new AssignWorldTabScreen(screen, wId, wName));
                            })
                            .bounds(scaledWidth / 2 + 4 + 105, 10, 100, 16)
                            .build();
                    Screens.getWidgets(screen).add(assignBtn);
                }
            }
        });
    }

    private static ServerData findServerData(Screen screen) {
        try {
            Class<?> cls = screen.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType() == ServerData.class) {
                        f.setAccessible(true);
                        return (ServerData) f.get(screen);
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {}
        return null;
    }

    private static String[] findWorldData(Screen screen) {
        try {
            Class<?> cls = screen.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("LevelSummary")) {
                        f.setAccessible(true);
                        Object summary = f.get(screen);
                        if (summary == null) continue;
                        
                        String id = WorldTabDropdownController.getLevelIdSafe(summary);
                        String name = WorldTabDropdownController.getLevelNameSafe(summary);
                        
                        return new String[] { id, name };
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {}
        return null;
    }
}
