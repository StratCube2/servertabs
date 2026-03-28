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

    // -----------------------------------------------------------------------
    //  Singleton
    // -----------------------------------------------------------------------

    private static TabConfig instance;

    public static TabConfig getInstance() {
        if (instance == null) { instance = new TabConfig(); instance.load(); }
        return instance;
    }

    // -----------------------------------------------------------------------
    //  Fields
    // -----------------------------------------------------------------------

    private List<TabEntry>           tabs                 = new ArrayList<>();
    private boolean                  dropdownEnabled      = true;
    private TransitionSpeed          transitionSpeed      = TransitionSpeed.MEDIUM;
    private SortingType              sortingType          = SortingType.NONE;
    private String                   defaultTabId         = "all";
    private boolean                  rememberTab          = true;
    private Map<String, Set<String>> serverTabAssignments = new HashMap<>();

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
            tabs.clear();
            if (obj.has("tabs"))
                for (JsonElement el : obj.getAsJsonArray("tabs"))
                    tabs.add(GSON.fromJson(el, TabEntry.class));
            if (obj.has("dropdownEnabled"))  dropdownEnabled = obj.get("dropdownEnabled").getAsBoolean();
            if (obj.has("transitionSpeed"))  transitionSpeed = TransitionSpeed.valueOf(obj.get("transitionSpeed").getAsString());
            if (obj.has("sortingType"))      sortingType     = SortingType.valueOf(obj.get("sortingType").getAsString());
            if (obj.has("defaultTabId"))     defaultTabId    = obj.get("defaultTabId").getAsString();
            if (obj.has("rememberTab"))      rememberTab     = obj.get("rememberTab").getAsBoolean();
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
        } catch (Exception e) {
            ServerTabsMod.LOGGER.error("[ServerTabs] Failed to load config", e);
            applyDefaults();
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.add("tabs", GSON.toJsonTree(tabs));
            obj.addProperty("dropdownEnabled", dropdownEnabled);
            obj.addProperty("transitionSpeed", transitionSpeed.name());
            obj.addProperty("sortingType",     sortingType.name());
            obj.addProperty("defaultTabId",    defaultTabId);
            obj.addProperty("rememberTab",     rememberTab);
            JsonObject a = new JsonObject();
            for (Map.Entry<String, Set<String>> e : serverTabAssignments.entrySet())
                a.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            obj.add("serverTabAssignments", a);
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
        serverTabAssignments = new HashMap<>();
    }

    private void ensureAllTab() {
        if (tabs.stream().noneMatch(t -> "all".equals(t.getId())))
            tabs.add(0, new TabEntry("all", "All", true));
    }

    // -----------------------------------------------------------------------
    //  Getters
    // -----------------------------------------------------------------------

    public List<TabEntry>  getTabs()            { return tabs; }
    public boolean         isDropdownEnabled()  { return dropdownEnabled; }
    public TransitionSpeed getTransitionSpeed() { return transitionSpeed; }
    public SortingType     getSortingType()     { return sortingType; }
    public String          getDefaultTabId()    { return defaultTabId; }
    public boolean         isRememberTab()      { return rememberTab; }

    public TabEntry getDefaultTab() {
        return tabs.stream().filter(t -> t.getId().equals(defaultTabId))
                   .findFirst().orElse(tabs.isEmpty() ? null : tabs.get(0));
    }

    // -----------------------------------------------------------------------
    //  Setters
    // -----------------------------------------------------------------------

    public void setDropdownEnabled(boolean v)         { dropdownEnabled = v; save(); }
    public void setTransitionSpeed(TransitionSpeed v) { transitionSpeed = v; save(); }
    public void setSortingType(SortingType v)         { sortingType = v;     save(); }
    public void setDefaultTabId(String v)             { defaultTabId = v;    save(); }
    public void setRememberTab(boolean v)             { rememberTab = v;     save(); }

    // -----------------------------------------------------------------------
    //  Tab mutations
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
    //  Server-tab assignments
    // -----------------------------------------------------------------------

    private static String normaliseIp(String ip) {
        return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
    }

    public void assignServer(String serverIp, String tabId) {
        if ("all".equals(tabId)) return;
        String key = normaliseIp(serverIp);
        if (key.isEmpty()) return;
        serverTabAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(tabId);
        save();
    }

    public void unassignServer(String serverIp, String tabId) {
        String key = normaliseIp(serverIp);
        Set<String> ids = serverTabAssignments.get(key);
        if (ids != null) { ids.remove(tabId); if (ids.isEmpty()) serverTabAssignments.remove(key); }
        save();
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
}
