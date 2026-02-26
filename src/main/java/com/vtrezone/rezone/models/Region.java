/*
 * VtReZone- Automatically resets a region to its original state.
 * Copyright (c) 2026 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */
package com.vtrezone.rezone.models;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Region {
    private String id;
    private World world;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int delay;
    private String filterType; 
    private List<String> filteredBlocks;
    private Map<String, Integer> customDelays;

    public Region(String id, World world, int x1, int y1, int z1, int x2, int y2, int z2, 
                  int delay, String filterType, List<String> filteredBlocks, Map<String, Integer> customDelays) {
        this.id = id;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.delay = delay;
        this.filterType = filterType;
        this.filteredBlocks = filteredBlocks;
        this.customDelays = customDelays != null ? customDelays : new HashMap<>();
    }

    public boolean contains(Location loc) {
        if (world == null || loc.getWorld() == null || !loc.getWorld().equals(world)) return false;
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
               loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
               loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public String getId() { return id; }
    public World getWorld() { return world; }
    public int getDelay() { return delay; }
    public String getFilterType() { return filterType; }
    public List<String> getFilteredBlocks() { return filteredBlocks; }
    public Map<String, Integer> getCustomDelays() { return customDelays; }
    
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public void setDelay(int delay) { this.delay = delay; }
    public void setFilterType(String filterType) { this.filterType = filterType; }
}