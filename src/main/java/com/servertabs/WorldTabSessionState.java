package com.servertabs;

/**
 * Static state holder for the active world tab, survives SelectWorldScreen reinits.
 * Automatically cleared when the game quits (static fields don't persist).
 *
 * Using null as sentinel means "not yet set — use the config default".
 */
public class WorldTabSessionState {

    private static String activeTabId = null;

    /** Returns the active world tab ID, falling back to the config default if not yet set. */
    public static String getActiveTabId() {
        if (activeTabId == null) {
            return TabConfig.getInstance().getWorldDefaultTabId();
        }
        return activeTabId;
    }

    /** Explicitly set the active world tab (persists across screen reinits). */
    public static void setActiveTabId(String id) {
        activeTabId = id;
    }

    /**
     * Reset to null so the next call to getActiveTabId() returns the config default.
     * Called when the user goes to the main menu and worldRememberTab is OFF.
     */
    public static void resetToDefault() {
        activeTabId = null;
    }
}
