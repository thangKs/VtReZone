/*
 * VtReZone- Automatically resets a region to its original state.
 * Copyright (c) 2026 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */
package com.vtrezone.rezone.managers;

import com.vtrezone.rezone.ReZonePlugin;
import com.vtrezone.rezone.models.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionManager {
    private final ReZonePlugin plugin;
    private File regionsFile;
    private FileConfiguration regionsConfig;
    private final Map<String, Region> regions = new HashMap<>();

    public RegionManager(ReZonePlugin plugin) {
        this.plugin = plugin;
        loadRegionsFile();
    }

    public void loadRegionsFile() {
        regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            try { regionsFile.createNewFile(); } catch (IOException ignored) {}
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
        regions.clear();

        if (regionsConfig.contains("regions")) {
            for (String key : regionsConfig.getConfigurationSection("regions").getKeys(false)) {
                String path = "regions." + key + ".";
                World world = Bukkit.getWorld(regionsConfig.getString(path + "world"));
                if (world == null) continue;

                String[] min = regionsConfig.getString(path + "min").split(",");
                String[] max = regionsConfig.getString(path + "max").split(",");
                int delay = regionsConfig.getInt(path + "delay", 300);
                String filterType = regionsConfig.getString(path + "filter-type", "NONE");
                List<String> filteredBlocks = regionsConfig.getStringList(path + "filtered-blocks");
                
                Map<String, Integer> customDelays = new HashMap<>();
                if (regionsConfig.contains(path + "custom-delays")) {
                    for (String mat : regionsConfig.getConfigurationSection(path + "custom-delays").getKeys(false)) {
                        customDelays.put(mat, regionsConfig.getInt(path + "custom-delays." + mat));
                    }
                }

                Region region = new Region(key, world, 
                    Integer.parseInt(min[0]), Integer.parseInt(min[1]), Integer.parseInt(min[2]),
                    Integer.parseInt(max[0]), Integer.parseInt(max[1]), Integer.parseInt(max[2]),
                    delay, filterType, filteredBlocks, customDelays);
                regions.put(key.toLowerCase(), region);
            }
        }
    }

    public void addRegion(String id, Location pos1, Location pos2) {
        int defaultDelay = 300; 
        String defaultFilter = "BLACKLIST";
        List<String> defaultBlocks = new ArrayList<>(Arrays.asList("TNT", "BEDROCK", "LAVA", "WATER"));
        Map<String, Integer> defaultCustomDelays = new HashMap<>();
        defaultCustomDelays.put("OBSIDIAN", 600);
        defaultCustomDelays.put("DIAMOND_BLOCK", 120);

        Region region = new Region(id, pos1.getWorld(), pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ(), defaultDelay, defaultFilter, defaultBlocks, defaultCustomDelays);
        
        regions.put(id.toLowerCase(), region);
        saveRegion(region);
    }

    public boolean removeRegion(String id) {
        if (regions.remove(id.toLowerCase()) != null) {
            regionsConfig.set("regions." + id.toLowerCase(), null);
            try { regionsConfig.save(regionsFile); } catch (IOException ignored) {}
            return true;
        }
        return false;
    }

    public void saveRegion(Region r) {
        regionsConfig.options().header(
            "VtReZone - Regions Data File\n" +
            "This file stores all region configurations automatically.\n" +
            "Fields explanation:\n" +
            "- world: The dimension of the region.\n" +
            "- min/max: Bounding box coordinates of the region.\n" +
            "- delay: The default reset delay in seconds.\n" +
            "- filter-type: Mode of operation (NONE, WHITELIST, or BLACKLIST).\n" +
            "- filtered-blocks: List of block materials affected by the filter.\n" +
            "- custom-delays: Specific delays for specific blocks."
        );
        regionsConfig.options().copyHeader(true);

        String path = "regions." + r.getId() + ".";
        regionsConfig.set(path + "world", r.getWorld().getName());
        regionsConfig.set(path + "min", r.getMinX() + "," + r.getMinY() + "," + r.getMinZ());
        regionsConfig.set(path + "max", r.getMaxX() + "," + r.getMaxY() + "," + r.getMaxZ());
        regionsConfig.set(path + "delay", r.getDelay());
        regionsConfig.set(path + "filter-type", r.getFilterType());
        regionsConfig.set(path + "filtered-blocks", r.getFilteredBlocks());
        
        regionsConfig.set(path + "custom-delays", null);
        for (Map.Entry<String, Integer> entry : r.getCustomDelays().entrySet()) {
            regionsConfig.set(path + "custom-delays." + entry.getKey(), entry.getValue());
        }
        
        try { regionsConfig.save(regionsFile); } catch (IOException ignored) {}
    }

    public Region getRegionAt(Location loc) {
        for (Region region : regions.values()) {
            if (region.contains(loc)) return region;
        }
        return null;
    }
    
    public Region getRegion(String id) {
        return regions.get(id.toLowerCase());
    }

    public List<Region> getAllRegions() {
        return new ArrayList<>(regions.values());
    }
}