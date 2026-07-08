package com.servertabs;

/**
 * Represents a single server tab.
 * Gson requires a no-arg constructor; all fields are package-accessible for serialization.
 */
public class TabEntry {

    private String  id;
    private String  name;
    private String  color; // Hex color string, e.g. "#FFFFFF"
    
    /** Locked tabs (e.g. "All") cannot be renamed, deleted, or moved below index 0. */
    private boolean locked;

    /** No-arg constructor required by Gson. */
    public TabEntry() {}

    public TabEntry(String id, String name, boolean locked, String color) {
        this.id     = id;
        this.name   = name;
        this.locked = locked;
        this.color  = color != null ? color : "#FFFFFF";
    }

    public String  getId()     { return id; }
    public String  getName()   { return name; }
    public boolean isLocked()  { return locked; }
    public String  getColor()  { return color != null ? color : "#FFFFFF"; }

    public void setId(String id)         { this.id = id; }
    public void setName(String name)     { this.name = name; }
    public void setLocked(boolean locked){ this.locked = locked; }
    public void setColor(String color)   { this.color = color; }

    /** Safely parses the HEX color string into an ARGB integer. */
    public int getParsedColor() {
        String c = getColor();
        try {
            if (c.startsWith("#")) {
                return 0xFF000000 | Integer.parseInt(c.substring(1), 16);
            }
            return 0xFF000000 | Integer.parseInt(c, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF; // Fallback to white
        }
    }
}