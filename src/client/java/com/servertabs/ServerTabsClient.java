package com.servertabs;

import com.servertabs.gui.AssignTabScreen;
import com.servertabs.gui.TabDropdownController;
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
import java.util.WeakHashMap;

public class ServerTabsClient implements ClientModInitializer {

    private static final WeakHashMap<Screen, TabDropdownController> controllers = new WeakHashMap<>();
    private static final WeakHashMap<Screen, Boolean> injectedScreens = new WeakHashMap<>();
    private static int lastKnownServerCount = -1;

    @Override
    public void onInitializeClient() {
        ServerTabsMod.LOGGER.info("ServerTabs initialized!");
        TabConfig.getInstance();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof JoinMultiplayerScreen jms) {
                TabDropdownController controller = controllers.computeIfAbsent(screen, s -> {
                    TabDropdownController ctrl = new TabDropdownController(s);
                    ScreenEvents.afterExtract(s).register(ctrl::onRender);
                    ScreenMouseEvents.allowMouseClick(s).register(ctrl::onMouseClick);
                    ScreenKeyboardEvents.allowKeyPress(s).register((sc, key) -> {
                        if ((key.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                            if (key.key() == GLFW.GLFW_KEY_W) { ctrl.switchTab(-1); return false; }
                            if (key.key() == GLFW.GLFW_KEY_S) { ctrl.switchTab(+1); return false; }
                        }
                        return true;
                    });
                    return ctrl;
                });

                Screens.getWidgets(screen).add(controller.createToggleButton());

                if (TabConfig.getInstance().isAssignOnAdd()) {
                    ServerList servers = jms.servers;
                    if (servers != null) {
                        servers.load();
                        int count = servers.size();
                        if (lastKnownServerCount >= 0 && count > lastKnownServerCount) {
                            ServerData newest = servers.get(count - 1);
                            client.execute(() -> client.setScreen(new AssignTabScreen(screen, newest.ip, newest.name)));
                        }
                        lastKnownServerCount = count;
                    }
                }
                TabDropdownController.applyTabFilter(jms, TabSessionState.getActiveTabId());
                return;
            }

            if (screen instanceof TitleScreen) {
                if (!TabConfig.getInstance().isRememberTab()) TabSessionState.resetToDefault();
                lastKnownServerCount = -1;
                TabDropdownController.clearCache(); // Clear memory cache
                return;
            }

            if (injectedScreens.containsKey(screen)) return;
            ServerData sd = findServerData(screen);
            if (sd != null) {
                injectedScreens.put(screen, true);
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Assign Tab"), 
                    btn -> client.setScreen(new AssignTabScreen(screen, sd.ip, sd.name)))
                    .bounds(scaledWidth / 2 + 109, 10, 100, 16).build());
            }
        });
    }

    private static ServerData findServerData(Screen screen) {
        Class<?> cls = screen.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == ServerData.class) {
                    try { f.setAccessible(true); return (ServerData) f.get(screen); } catch (Exception e) { return null; }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}