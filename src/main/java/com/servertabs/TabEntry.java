package com.servertabs;

/**
 * Represents a single server tab.
 * Gson requires a no-arg constructor; all fields are package-accessible for serialization.
 */
public class TabEntry {

    private String  id;
    private String  name;
    /** Locked tabs (e.g. "All") cannot be renamed, deleted, or moved below index 0. */
    private boolean locked;

    /** No-arg constructor required by Gson. */
    public TabEntry() {}

    public TabEntry(String id, String name, boolean locked) {
        this.id     = id;
        this.name   = name;
        this.locked = locked;
    }

    public String  getId()     { return id; }
    public String  getName()   { return name; }
    public boolean isLocked()  { return locked; }

    public void setId(String id)         { this.id = id; }
    public void setName(String name)     { this.name = name; }
    public void setLocked(boolean locked){ this.locked = locked; }
}
