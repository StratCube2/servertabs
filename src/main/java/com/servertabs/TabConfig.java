package com.servertabs;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class TabConfig {

    // -----------------------------------------------------------------------
    //  Enums
    // -----------------------------------------------------------------------

    public enum TransitionSpeed {
        SLOW  (0.05f, "Slow"),
        MEDIUM(0.10f, "Medium"),
        FAST  (0.20f, "Fast");

        public final float value;
        public final String label;

        TransitionSpeed(float value, String label) { this.value = value; this.label = label; }

        public TransitionSpeed next() {
            TransitionSpeed[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum SortingType {
        NONE("None"), PING("Ping"), ALPHABETICAL("Alphabetical"), PLAYER_COUNT("Player Count");

        public final String label;
        SortingType(String label) { this.label = label; }

        public SortingType next() {
            SortingType[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum WorldSortingType {
        NONE("None"), ALPHABETICAL("Alphabetical"), LAST_PLAYED("Last Played");

        public final String label;
        WorldSortingType(String label) { this.label = label; }

        public WorldSortingType next() {
            WorldSortingType[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    // -----------------------------------------------------------------------
    //  Singleton
    // -----------------------------------------------------------------------

    private static TabConfig instance;

    public static TabConfig getInstance() {
        if (instance == null) { instance = new TabConfig(); instance.load(); }
        return instance;
    }

    // -----------------------------------------------------------------------
    //  Fields — Server Tabs
    // -----------------------------------------------------------------------

    private List<TabEntry>           tabs                 = new ArrayList<>();
    private boolean                  dropdownEnabled      = true;
    private TransitionSpeed          transitionSpeed      = TransitionSpeed.MEDIUM;
    private SortingType              sortingType          = SortingType.NONE;
    private String                   defaultTabId         = "all";
    private boolean                  rememberTab          = true;
    private boolean                  assignOnAdd          = true;
    private Map<String, Set<String>> serverTabAssignments = new HashMap<>();

    // -----------------------------------------------------------------------
    //  Fields — World Tabs
    // -----------------------------------------------------------------------

    private List<TabEntry>           worldTabs                = new ArrayList<>();
    private boolean                  worldTabsEnabled         = true;
    private boolean                  worldDropdownEnabled     = true;
    private TransitionSpeed          worldTransitionSpeed     = TransitionSpeed.MEDIUM;
    private WorldSortingType         worldSortingType         = WorldSortingType.NONE;
    private String                   worldDefaultTabId        = "all";
    private boolean                  worldRememberTab         = false;
    private boolean                  worldAssignOnAdd         = false;
    private Map<String, Set<String>> worldTabAssignments      = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -----------------------------------------------------------------------
    //  Load / Save
    // -----------------------------------------------------------------------

    private Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("servertabs.json");
    }

    public void load() {
        File file = configPath().toFile();
        if (!file.exists()) { applyDefaults(); save(); return; }
        try (Reader r = new FileReader(file)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);

            // — Server tabs —
            tabs.clear();
            if (obj.has("tabs"))
                for (JsonElement el : obj.getAsJsonArray("tabs"))
                    tabs.add(GSON.fromJson(el, TabEntry.class));
            if (obj.has("dropdownEnabled"))  dropdownEnabled = obj.get("dropdownEnabled").getAsBoolean();
            if (obj.has("transitionSpeed"))  transitionSpeed = TransitionSpeed.valueOf(obj.get("transitionSpeed").getAsString());
            if (obj.has("sortingType"))      sortingType     = SortingType.valueOf(obj.get("sortingType").getAsString());
            if (obj.has("defaultTabId"))     defaultTabId    = obj.get("defaultTabId").getAsString();
            if (obj.has("rememberTab"))      rememberTab     = obj.get("rememberTab").getAsBoolean();
            if (obj.has("assignOnAdd"))      assignOnAdd     = obj.get("assignOnAdd").getAsBoolean();
            serverTabAssignments.clear();
            if (obj.has("serverTabAssignments")) {
                JsonObject a = obj.getAsJsonObject("serverTabAssignments");
                for (Map.Entry<String, JsonElement> e : a.entrySet()) {
                    Set<String> ids = new HashSet<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) ids.add(el.getAsString());
                    serverTabAssignments.put(e.getKey(), ids);
                }
            }
            ensureAllTab();

            // — World tabs —
            worldTabs.clear();
            if (obj.has("worldTabs"))
                for (JsonElement el : obj.getAsJsonArray("worldTabs"))
                    worldTabs.add(GSON.fromJson(el, TabEntry.class));
            if (obj.has("worldTabsEnabled"))     worldTabsEnabled     = obj.get("worldTabsEnabled").getAsBoolean();
            if (obj.has("worldDropdownEnabled")) worldDropdownEnabled = obj.get("worldDropdownEnabled").getAsBoolean();
            if (obj.has("worldTransitionSpeed")) worldTransitionSpeed = TransitionSpeed.valueOf(obj.get("worldTransitionSpeed").getAsString());
            if (obj.has("worldSortingType"))     worldSortingType     = WorldSortingType.valueOf(obj.get("worldSortingType").getAsString());
            if (obj.has("worldDefaultTabId"))    worldDefaultTabId    = obj.get("worldDefaultTabId").getAsString();
            if (obj.has("worldRememberTab"))     worldRememberTab     = obj.get("worldRememberTab").getAsBoolean();
            if (obj.has("worldAssignOnAdd"))     worldAssignOnAdd     = obj.get("worldAssignOnAdd").getAsBoolean();
            worldTabAssignments.clear();
            if (obj.has("worldTabAssignments")) {
                JsonObject a = obj.getAsJsonObject("worldTabAssignments");
                for (Map.Entry<String, JsonElement> e : a.entrySet()) {
                    Set<String> ids = new HashSet<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) ids.add(el.getAsString());
                    worldTabAssignments.put(e.getKey(), ids);
                }
            }
            ensureAllWorldTab();

        } catch (Exception e) {
            ServerTabsMod.LOGGER.error("[ServerTabs] Failed to load config", e);
            applyDefaults();
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();

            // — Server tabs —
            obj.add("tabs", GSON.toJsonTree(tabs));
            obj.addProperty("dropdownEnabled", dropdownEnabled);
            obj.addProperty("transitionSpeed", transitionSpeed.name());
            obj.addProperty("sortingType",     sortingType.name());
            obj.addProperty("defaultTabId",    defaultTabId);
            obj.addProperty("rememberTab",     rememberTab);
            obj.addProperty("assignOnAdd",     assignOnAdd);
            JsonObject sa = new JsonObject();
            for (Map.Entry<String, Set<String>> e : serverTabAssignments.entrySet())
                sa.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            obj.add("serverTabAssignments", sa);

            // — World tabs —
            obj.add("worldTabs", GSON.toJsonTree(worldTabs));
            obj.addProperty("worldTabsEnabled",     worldTabsEnabled);
            obj.addProperty("worldDropdownEnabled", worldDropdownEnabled);
            obj.addProperty("worldTransitionSpeed", worldTransitionSpeed.name());
            obj.addProperty("worldSortingType",     worldSortingType.name());
            obj.addProperty("worldDefaultTabId",    worldDefaultTabId);
            obj.addProperty("worldRememberTab",     worldRememberTab);
            obj.addProperty("worldAssignOnAdd",     worldAssignOnAdd);
            JsonObject wa = new JsonObject();
            for (Map.Entry<String, Set<String>> e : worldTabAssignments.entrySet())
                wa.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            obj.add("worldTabAssignments", wa);

            try (Writer w = new FileWriter(configPath().toFile())) { GSON.toJson(obj, w); }
        } catch (Exception e) {
            ServerTabsMod.LOGGER.error("[ServerTabs] Failed to save config", e);
        }
    }

    private void applyDefaults() {
        tabs.clear();
        tabs.add(new TabEntry("all",       "All",       true));
        tabs.add(new TabEntry("friends",   "Friends",   false));
        tabs.add(new TabEntry("favorites", "Favorites", false));
        dropdownEnabled      = true;
        transitionSpeed      = TransitionSpeed.MEDIUM;
        sortingType          = SortingType.NONE;
        defaultTabId         = "all";
        rememberTab          = true;
        assignOnAdd          = true;
        serverTabAssignments = new HashMap<>();

        worldTabs.clear();
        worldTabs.add(new TabEntry("all", "All", true));
        worldTabsEnabled     = true;
        worldDropdownEnabled = true;
        worldTransitionSpeed = TransitionSpeed.MEDIUM;
        worldSortingType     = WorldSortingType.NONE;
        worldDefaultTabId    = "all";
        worldRememberTab     = false;
        worldAssignOnAdd     = false;
        worldTabAssignments  = new HashMap<>();
    }

    private void ensureAllTab() {
        if (tabs.stream().noneMatch(t -> "all".equals(t.getId())))
            tabs.add(0, new TabEntry("all", "All", true));
    }

    private void ensureAllWorldTab() {
        if (worldTabs.stream().noneMatch(t -> "all".equals(t.getId())))
            worldTabs.add(0, new TabEntry("all", "All", true));
    }

    // -----------------------------------------------------------------------
    //  Getters — Server Tabs
    // -----------------------------------------------------------------------

    public List<TabEntry>  getTabs()            { return tabs; }
    public boolean         isDropdownEnabled()  { return dropdownEnabled; }
    public TransitionSpeed getTransitionSpeed() { return transitionSpeed; }
    public SortingType     getSortingType()     { return sortingType; }
    public String          getDefaultTabId()    { return defaultTabId; }
    public boolean         isRememberTab()      { return rememberTab; }
    public boolean         isAssignOnAdd()      { return assignOnAdd; }

    public TabEntry getDefaultTab() {
        return tabs.stream().filter(t -> t.getId().equals(defaultTabId))
                   .findFirst().orElse(tabs.isEmpty() ? null : tabs.get(0));
    }

    // -----------------------------------------------------------------------
    //  Getters — World Tabs
    // -----------------------------------------------------------------------

    public List<TabEntry>   getWorldTabs()            { return worldTabs; }
    public boolean          isWorldTabsEnabled()      { return worldTabsEnabled; }
    public boolean          isWorldDropdownEnabled()  { return worldDropdownEnabled; }
    public TransitionSpeed  getWorldTransitionSpeed() { return worldTransitionSpeed; }
    public WorldSortingType getWorldSortingType()     { return worldSortingType; }
    public String           getWorldDefaultTabId()    { return worldDefaultTabId; }
    public boolean          isWorldRememberTab()      { return worldRememberTab; }
    public boolean          isWorldAssignOnAdd()      { return worldAssignOnAdd; }

    public TabEntry getWorldDefaultTab() {
        return worldTabs.stream().filter(t -> t.getId().equals(worldDefaultTabId))
                        .findFirst().orElse(worldTabs.isEmpty() ? null : worldTabs.get(0));
    }

    // -----------------------------------------------------------------------
    //  Setters — Server Tabs
    // -----------------------------------------------------------------------

    public void setDropdownEnabled(boolean v)         { dropdownEnabled = v; save(); }
    public void setTransitionSpeed(TransitionSpeed v) { transitionSpeed = v; save(); }
    public void setSortingType(SortingType v)         { sortingType = v;     save(); }
    public void setDefaultTabId(String v)             { defaultTabId = v;    save(); }
    public void setRememberTab(boolean v)             { rememberTab = v;     save(); }
    public void setAssignOnAdd(boolean v)             { assignOnAdd = v;     save(); }

    // -----------------------------------------------------------------------
    //  Setters — World Tabs
    // -----------------------------------------------------------------------

    public void setWorldTabsEnabled(boolean v)           { worldTabsEnabled = v;     save(); }
    public void setWorldDropdownEnabled(boolean v)       { worldDropdownEnabled = v; save(); }
    public void setWorldTransitionSpeed(TransitionSpeed v) { worldTransitionSpeed = v; save(); }
    public void setWorldSortingType(WorldSortingType v)  { worldSortingType = v;     save(); }
    public void setWorldDefaultTabId(String v)           { worldDefaultTabId = v;    save(); }
    public void setWorldRememberTab(boolean v)           { worldRememberTab = v;     save(); }
    public void setWorldAssignOnAdd(boolean v)           { worldAssignOnAdd = v;     save(); }

    // -----------------------------------------------------------------------
    //  Tab mutations — Server Tabs
    // -----------------------------------------------------------------------

    public void addTab(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_")
                    + "_" + System.currentTimeMillis();
        tabs.add(new TabEntry(id, name, false));
        save();
    }

    public void renameTab(TabEntry tab, String newName) {
        if (tab.isLocked()) return;
        tab.setName(newName); save();
    }

    public void deleteTab(TabEntry tab) {
        if (tab.isLocked()) return;
        String id = tab.getId();
        serverTabAssignments.values().forEach(s -> s.remove(id));
        tabs.remove(tab);
        if (defaultTabId.equals(id)) defaultTabId = "all";
        save();
    }

    public void moveTabUp(int index) {
        if (index <= 0 || index >= tabs.size()) return;
        if (tabs.get(index - 1).isLocked()) return;
        Collections.swap(tabs, index, index - 1); save();
    }

    public void moveTabDown(int index) {
        if (index < 0 || index >= tabs.size() - 1) return;
        if (tabs.get(index).isLocked()) return;
        Collections.swap(tabs, index, index + 1); save();
    }

    // -----------------------------------------------------------------------
    //  Tab mutations — World Tabs
    // -----------------------------------------------------------------------

    public void addWorldTab(String name) {
        String id = "world_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_")
                    + "_" + System.currentTimeMillis();
        worldTabs.add(new TabEntry(id, name, false));
        save();
    }

    public void renameWorldTab(TabEntry tab, String newName) {
        if (tab.isLocked()) return;
        tab.setName(newName); save();
    }

    public void deleteWorldTab(TabEntry tab) {
        if (tab.isLocked()) return;
        String id = tab.getId();
        worldTabAssignments.values().forEach(s -> s.remove(id));
        worldTabs.remove(tab);
        if (worldDefaultTabId.equals(id)) worldDefaultTabId = "all";
        save();
    }

    public void moveWorldTabUp(int index) {
        if (index <= 0 || index >= worldTabs.size()) return;
        if (worldTabs.get(index - 1).isLocked()) return;
        Collections.swap(worldTabs, index, index - 1); save();
    }

    public void moveWorldTabDown(int index) {
        if (index < 0 || index >= worldTabs.size() - 1) return;
        if (worldTabs.get(index).isLocked()) return;
        Collections.swap(worldTabs, index, index + 1); save();
    }

    // -----------------------------------------------------------------------
    //  Server-tab assignments
    // -----------------------------------------------------------------------

    private static String normaliseIp(String ip) {
        return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
    }

    public void assignServer(String serverIp, String tabId) {
        assignServer(serverIp, tabId, true);
    }

    public void assignServer(String serverIp, String tabId, boolean doSave) {
        if ("all".equals(tabId)) return;
        String key = normaliseIp(serverIp);
        if (key.isEmpty()) return;
        serverTabAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(tabId);
        if (doSave) save();
    }

    public void unassignServer(String serverIp, String tabId) {
        unassignServer(serverIp, tabId, true);
    }

    public void unassignServer(String serverIp, String tabId, boolean doSave) {
        String key = normaliseIp(serverIp);
        Set<String> ids = serverTabAssignments.get(key);
        if (ids != null) { ids.remove(tabId); if (ids.isEmpty()) serverTabAssignments.remove(key); }
        if (doSave) save();
    }

    public boolean serverInTab(String serverIp, String tabId) {
        if ("all".equals(tabId)) return true;
        String key = normaliseIp(serverIp);
        Set<String> ids = serverTabAssignments.get(key);
        return ids != null && ids.contains(tabId);
    }

    public Set<String> getTabIdsForServer(String serverIp) {
        String key = normaliseIp(serverIp);
        Set<String> ids = serverTabAssignments.get(key);
        return ids != null ? Collections.unmodifiableSet(ids) : Collections.emptySet();
    }

    public boolean toggleServerTab(String serverIp, String tabId) {
        if ("all".equals(tabId)) return true;
        if (serverInTab(serverIp, tabId)) { unassignServer(serverIp, tabId); return false; }
        else { assignServer(serverIp, tabId); return true; }
    }

    /** Removes the server from all non-locked tabs in one save. */
    public void deselectAllServerTabs(String serverIp) {
        String key = normaliseIp(serverIp);
        if (!key.isEmpty()) serverTabAssignments.remove(key);
        save();
    }

    // -----------------------------------------------------------------------
    //  World-tab assignments
    // -----------------------------------------------------------------------

    private static String normaliseWorldId(String worldId) {
        return worldId == null ? "" : worldId.trim();
    }

    public void assignWorld(String worldId, String tabId) {
        assignWorld(worldId, tabId, true);
    }

    public void assignWorld(String worldId, String tabId, boolean doSave) {
        if ("all".equals(tabId)) return;
        String key = normaliseWorldId(worldId);
        if (key.isEmpty()) return;
        worldTabAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(tabId);
        if (doSave) save();
    }

    public void unassignWorld(String worldId, String tabId) {
        unassignWorld(worldId, tabId, true);
    }

    public void unassignWorld(String worldId, String tabId, boolean doSave) {
        String key = normaliseWorldId(worldId);
        Set<String> ids = worldTabAssignments.get(key);
        if (ids != null) { ids.remove(tabId); if (ids.isEmpty()) worldTabAssignments.remove(key); }
        if (doSave) save();
    }

    public boolean worldInTab(String worldId, String tabId) {
        if ("all".equals(tabId)) return true;
        String key = normaliseWorldId(worldId);
        Set<String> ids = worldTabAssignments.get(key);
        return ids != null && ids.contains(tabId);
    }

    public Set<String> getTabIdsForWorld(String worldId) {
        String key = normaliseWorldId(worldId);
        Set<String> ids = worldTabAssignments.get(key);
        return ids != null ? Collections.unmodifiableSet(ids) : Collections.emptySet();
    }

    public boolean toggleWorldTab(String worldId, String tabId) {
        if ("all".equals(tabId)) return true;
        if (worldInTab(worldId, tabId)) { unassignWorld(worldId, tabId); return false; }
        else { assignWorld(worldId, tabId); return true; }
    }

    /** Removes the world from all non-locked world tabs in one save. */
    public void deselectAllWorldTabs(String worldId) {
        String key = normaliseWorldId(worldId);
        if (!key.isEmpty()) worldTabAssignments.remove(key);
        save();
    }
}
