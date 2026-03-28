package com.servertabs;

import com.servertabs.gui.AssignTabScreen;
import com.servertabs.gui.TabDropdownController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

public class ServerTabsClient implements ClientModInitializer {

    /**
     * Tracks controllers by screen INSTANCE (not class).
     * WeakHashMap: when the screen is GC'd the entry is automatically removed.
     *
     * Purpose: when the SAME screen instance reinits (refresh button, returning
     * from AssignTabScreen), we reuse the existing controller and only re-add
     * the toggle button — avoiding duplicate afterRender/allowMouseClick listeners.
     */
    private static final WeakHashMap<Screen, TabDropdownController> controllers
            = new WeakHashMap<>();

    @Override
    public void onInitializeClient() {
        ServerTabsMod.LOGGER.info("ServerTabs client initialized!");
        TabConfig.getInstance(); // load/create config on startup

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
                    // Per-instance events registered only once
                    ScreenEvents.afterRender(screen).register(controller::onRender);
                    ScreenMouseEvents.allowMouseClick(screen).register(controller::onMouseClick);
                }

                // Buttons are cleared on every reinit — re-add every time
                Screens.getButtons(screen).add(controller.createToggleButton());

                // Apply the active tab filter after the screen has fully
                // initialized (so serverSelectionList is non-null).
                // applyTabFilter reloads from disk + calls updateOnlineServers,
                // which rebuilds the visual list correctly.
                TabDropdownController.applyTabFilter(jms, TabSessionState.getActiveTabId());
                return;
            }

            // ----------------------------------------------------------------
            // Main menu — reset tab if rememberTab is OFF
            // ----------------------------------------------------------------
            if (screen instanceof TitleScreen) {
                if (!TabConfig.getInstance().isRememberTab()) {
                    TabSessionState.resetToDefault();
                }
                return;
            }

            // ----------------------------------------------------------------
            // Add/Edit server screen — inject "Assign Tab" button
            // ----------------------------------------------------------------
            ServerData serverData = findServerData(screen);
            if (serverData != null) {
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
}
