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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

public class ServerTabsClient implements ClientModInitializer {

    /**
     * Tracks controllers by JoinMultiplayerScreen INSTANCE.
     * WeakHashMap: when the screen is GC'd the entry is removed automatically.
     *
     * Events (afterExtractRenderState, allowMouseClick) are registered only once per
     * screen instance — this prevents duplicate listeners on reinit.
     */
    private static final WeakHashMap<Screen, TabDropdownController> controllers
            = new WeakHashMap<>();

    /**
     * Tracks which non-JMS screens have already received our injected button,
     * to prevent adding duplicates when the screen reinits (e.g. returning
     * from AssignTabScreen back to EditServerScreen).
     *
     * Bug 1 root fix: without this, AFTER_INIT fires again on EditServerScreen
     * after AssignTabScreen closes, adding a second "Assign Tab" button and
     * leaving the screen in an inconsistent state that breaks the JMS dropdown.
     */
    private static final WeakHashMap<Screen, Boolean> injectedScreens
            = new WeakHashMap<>();

    /**
     * Tracks server count as of the last JMS init, so we can detect when a
     * new server was just added (Feature 3: Assign on Add).
     */
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

                // Reuse or create the controller for this screen instance
                TabDropdownController controller = controllers.get(screen);
                if (controller == null) {
                    controller = new TabDropdownController(screen);
                    controllers.put(screen, controller);
                    ScreenEvents.afterExtract(screen).register(controller::onRender);
                    ScreenMouseEvents.allowMouseClick(screen).register(controller::onMouseClick);

                    // Feature 4: Alt+W (prev tab) / Alt+S (next tab)
                    final TabDropdownController ctrl = controller;
                    ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                        if ((keyEvent.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                            if (keyEvent.key() == GLFW.GLFW_KEY_W) { ctrl.switchTab(-1); return false; }
                            if (keyEvent.key() == GLFW.GLFW_KEY_S) { ctrl.switchTab(+1); return false; }
                        }
                        return true;
                    });
                }

                // createToggleButton() resets panelOpen/slideProgress (bug 1 fix)
                Screens.getWidgets(screen).add(controller.createToggleButton());

                // ── Feature 3: Assign on Add ─────────────────────────────
                // Check if a new server was just added by comparing the full
                // (unfiltered) server count to what we saw last time.
                if (TabConfig.getInstance().isAssignOnAdd()) {
                    ServerList servers = jms.servers;
                    if (servers != null) {
                        servers.load(); // get full unfiltered count
                        int currentCount = servers.size();

                        if (lastKnownServerCount >= 0
                                && currentCount > lastKnownServerCount) {
                            // A new server was added — get the last entry
                            ServerData newest = servers.get(currentCount - 1);
                            if (newest != null) {
                                String ip   = newest.ip   != null ? newest.ip.trim()   : "";
                                String name = newest.name != null ? newest.name.trim() : "";
                                // Navigate to AssignTabScreen for the new server.
                                // We post this via setScreen so the JMS fully
                                // finishes initializing before we navigate away.
                                final Screen jmsScreen = screen;
                                client.execute(() ->
                                    client.setScreen(new AssignTabScreen(jmsScreen, ip, name))
                                );
                                lastKnownServerCount = currentCount;
                                return; // skip applyTabFilter — we're navigating away
                            }
                        }
                        lastKnownServerCount = currentCount;
                    }
                }

                // Apply the active tab filter
                TabDropdownController.applyTabFilter(jms, TabSessionState.getActiveTabId());
                return;
            }

            // ----------------------------------------------------------------
            // Main menu — reset tab + server count tracking if rememberTab OFF
            // ----------------------------------------------------------------
            if (screen instanceof TitleScreen) {
                if (!TabConfig.getInstance().isRememberTab()) {
                    TabSessionState.resetToDefault();
                }
                // Reset server count so we don't false-positive on next JMS open
                lastKnownServerCount = -1;
                return;
            }

            // ----------------------------------------------------------------
            // Add/Edit server screen — inject "Assign Tab" button (once only)
            //
            // Bug 1 fix: we track which screen instances have already been
            // injected. Without this, returning from AssignTabScreen causes
            // AFTER_INIT to fire again on EditServerScreen, adding a second
            // button and corrupting state that breaks the JMS tab dropdown.
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
                Screens.getWidgets(screen).add(assignBtn);
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
}
