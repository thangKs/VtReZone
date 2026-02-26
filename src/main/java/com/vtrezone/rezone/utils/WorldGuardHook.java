/*
 * VtReZone- Automatically resets a region to its original state.
 * Copyright (c) 2026 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */
package com.vtrezone.rezone.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class WorldGuardHook {
    
    public static boolean hasWorldGuard() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public static Location[] getSavedRegion(World world, String regionName) {
        try {
            RegionManager wgrm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (wgrm != null) {
                ProtectedRegion wgRegion = wgrm.getRegion(regionName);
                if (wgRegion != null) {
                    Location pos1 = new Location(world, wgRegion.getMinimumPoint().getX(), wgRegion.getMinimumPoint().getY(), wgRegion.getMinimumPoint().getZ());
                    Location pos2 = new Location(world, wgRegion.getMaximumPoint().getX(), wgRegion.getMaximumPoint().getY(), wgRegion.getMaximumPoint().getZ());
                    return new Location[]{pos1, pos2};
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}