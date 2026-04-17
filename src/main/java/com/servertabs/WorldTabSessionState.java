package com.servertabs;

public class WorldTabSessionState {

    private static String activeTabId = null;

    public static String getActiveTabId() {
        if (activeTabId == null) {
            return TabConfig.getInstance().getWorldDefaultTabId();
        }
        return activeTabId;
    }

    public static void setActiveTabId(String id) {
        activeTabId = id;
    }

    public static void resetToDefault() {
        activeTabId = null;
    }
}