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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class RegenManager implements Listener {
    private final ReZonePlugin plugin;
    
    private final PriorityQueue<RestoreTask> queue = new PriorityQueue<>();
    private final Map<Location, RestoreTask> pendingLocations = new HashMap<>();

    public RegenManager(ReZonePlugin plugin) {
        this.plugin = plugin;
        startRegenTask();
        startParticleTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleInteraction(event.getBlock().getLocation(), event.getBlockReplacedState(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleInteraction(event.getBlock().getLocation(), event.getBlock().getState(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) handleInteraction(block.getLocation(), block.getState(), block.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) handleInteraction(block.getLocation(), block.getState(), block.getType());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        handleInteraction(event.getBlock().getLocation(), event.getBlock().getState(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlockClicked();
        handleInteraction(block.getLocation(), block.getState(), event.getBucket());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        handleInteraction(block.getLocation(), block.getState(), event.getBucket());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (plugin.getRegionManager().getRegionAt(toBlock.getLocation()) != null) {
            handleInteraction(toBlock.getLocation(), toBlock.getState(), event.getBlock().getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (plugin.getRegionManager().getRegionAt(event.getBlock().getLocation()) != null) {
            handleInteraction(event.getBlock().getLocation(), event.getBlock().getState(), event.getNewState().getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        for (BlockState state : event.getBlocks()) {
            Block b = state.getBlock();
            if (plugin.getRegionManager().getRegionAt(b.getLocation()) != null) {
                handleInteraction(b.getLocation(), b.getState(), Material.SPONGE);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null) {
            if (event.getItem().getType() == Material.END_CRYSTAL) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock != null) {
                    handleInteraction(clickedBlock.getLocation(), clickedBlock.getState(), clickedBlock.getType());
                }
            }
        }
    }

    private boolean isIgnoredByFilter(Region region, Material mat) {
        if (region == null) return true;
        String filterType = region.getFilterType().toUpperCase();
        if (filterType.equals("NONE")) return false;

        String matName = mat.name();
        boolean inList = region.getFilteredBlocks().contains(matName);
        
        if (matName.contains("WATER")) {
            inList = inList || region.getFilteredBlocks().contains("WATER") || region.getFilteredBlocks().contains("WATER_BUCKET");
        } else if (matName.contains("LAVA")) {
            inList = inList || region.getFilteredBlocks().contains("LAVA") || region.getFilteredBlocks().contains("LAVA_BUCKET");
        }

        if (filterType.equals("WHITELIST") && !inList) return true;
        if (filterType.equals("BLACKLIST") && inList) return true;

        return false;
    }

    private void handleInteraction(Location loc, BlockState originalState, Material interactedMat) {
        Region region = plugin.getRegionManager().getRegionAt(loc);
        if (region == null) return;

        if (isIgnoredByFilter(region, interactedMat)) return;

        if (pendingLocations.containsKey(loc)) {
            RestoreTask existingTask = pendingLocations.get(loc);
            int delay = region.getCustomDelays().getOrDefault(interactedMat.name(), -1);
            if (delay == -1) delay = region.getDelay();
            if (delay < 1) delay = 1;
            existingTask.executeTime = System.currentTimeMillis() + (delay * 1000L);
            
            queue.remove(existingTask); 
            queue.add(existingTask);
            return;
        }

        if (isLiquid(originalState.getType())) {
            if (originalState.getBlockData() instanceof org.bukkit.block.data.Levelled) {
                org.bukkit.block.data.Levelled levelled = (org.bukkit.block.data.Levelled) originalState.getBlockData();
                if (levelled.getLevel() > 0) {
                    originalState.setType(Material.AIR); 
                }
            }
        }

        int delay = region.getCustomDelays().getOrDefault(interactedMat.name(), -1);
        if (delay == -1) delay = region.getDelay();
        if (delay < 1) delay = 1;

        long executeTime = System.currentTimeMillis() + (delay * 1000L);
        RestoreTask task = new RestoreTask(loc, originalState, executeTime);
        queue.add(task);
        pendingLocations.put(loc, task);
    }

    public void updateRegionMemory(Region region) {
        List<RestoreTask> toRemove = new ArrayList<>();
        for (RestoreTask task : queue) {
            if (region.contains(task.loc)) toRemove.add(task);
        }
        for (RestoreTask task : toRemove) {
            queue.remove(task);
            pendingLocations.remove(task.loc);
        }
    }
    
    private boolean isLiquid(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN;
    }

    private void startRegenTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            int limit = plugin.getConfig().getInt("optimization.blocks-per-tick", 50);
            int processed = 0;

            while (!queue.isEmpty() && queue.peek().executeTime <= now && processed < limit) {
                RestoreTask task = queue.poll();
                pendingLocations.remove(task.loc);
                
                Block currentBlock = task.loc.getBlock();
                Material curMat = currentBlock.getType();
                Region region = plugin.getRegionManager().getRegionAt(task.loc);
                
                boolean isSame = curMat == task.state.getType() && 
                                 currentBlock.getBlockData().getAsString().equals(task.state.getBlockData().getAsString());
                
                if (!isSame) {
                    boolean isBreaking = task.state.getType() == Material.AIR || isLiquid(task.state.getType());
                    Material soundMat = isBreaking ? curMat : task.state.getType();

                    if (isLiquid(curMat) && region != null && !isLiquid(task.state.getType())) {
                        if (!isIgnoredByFilter(region, curMat)) {
                            task.state.update(true, false);
                            playRestoreEffects(task.loc, soundMat, true);
                            killStubbornLiquid(task.loc, region);
                            processed++;
                        } else {
                            boolean needsPhysics = isLiquid(curMat) || isLiquid(task.state.getType());
                            task.state.update(true, needsPhysics);
                            playRestoreEffects(task.loc, soundMat, isBreaking);
                            processed++;
                        }
                    } else {
                        boolean needsPhysics = isLiquid(curMat) || isLiquid(task.state.getType());
                        task.state.update(true, needsPhysics);
                        playRestoreEffects(task.loc, soundMat, isBreaking);
                        processed++;
                    }
                } else {
                    processed++;
                }

                for (Entity ent : task.loc.getWorld().getNearbyEntities(task.loc.clone().add(0.5, 1.0, 0.5), 0.5, 1.0, 0.5)) {
                    if (ent.getType() == EntityType.ENDER_CRYSTAL || ent.getType() == EntityType.FALLING_BLOCK) {
                        ent.remove();
                    }
                }
            }
        }, 1L, 1L);
    }

    private void killStubbornLiquid(Location start, Region region) {
        if (region == null) return;
        Queue<Location> bfsQueue = new LinkedList<>();
        bfsQueue.add(start);
        Set<Location> visited = new HashSet<>();
        visited.add(start);
        
        int count = 0;
        BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        
        while(!bfsQueue.isEmpty() && count < 2000) {
            Location curr = bfsQueue.poll();
            for (BlockFace face : faces) {
                Block neighbor = curr.getBlock().getRelative(face);
                Location nLoc = neighbor.getLocation();
                
                if (!visited.contains(nLoc) && region.contains(nLoc) && isLiquid(neighbor.getType())) {
                    visited.add(nLoc);
                    
                    if (isIgnoredByFilter(region, neighbor.getType())) continue;
                    
                    boolean shouldClear = false;
                    if (neighbor.getBlockData() instanceof org.bukkit.block.data.Levelled) {
                        org.bukkit.block.data.Levelled levelled = (org.bukkit.block.data.Levelled) neighbor.getBlockData();
                        if (levelled.getLevel() > 0) {
                            shouldClear = true; 
                        } else {
                            RestoreTask t = pendingLocations.get(nLoc);
                            if (t != null && !isLiquid(t.state.getType())) {
                                shouldClear = true;
                            }
                        }
                    }
                    
                    if (shouldClear) {
                        RestoreTask t = pendingLocations.remove(nLoc);
                        if (t != null) {
                            queue.remove(t);
                            t.state.update(true, false); 
                        } else {
                            neighbor.setType(Material.AIR, false); 
                        }
                        bfsQueue.add(nLoc);
                        count++;
                    }
                }
            }
        }
        
        for (Location loc : visited) {
            loc.getBlock().getState().update(true, true);
        }
    }

    private void startParticleTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("region-particles.enabled")) return;
            try {
                Particle p = Particle.valueOf(plugin.getConfig().getString("region-particles.particle-type", "FLAME"));
                for (Region r : plugin.getRegionManager().getAllRegions()) {
                    if (r.getWorld() == null) continue;
                    drawRegionBox(r, p);
                }
            } catch (Exception ignored) {}
        }, 0L, plugin.getConfig().getInt("region-particles.update-interval", 40));
    }

    private void drawRegionBox(Region r, Particle p) {
        World w = r.getWorld();
        double minX = r.getMinX(), minY = r.getMinY(), minZ = r.getMinZ();
        double maxX = r.getMaxX() + 1, maxY = r.getMaxY() + 1, maxZ = r.getMaxZ() + 1;
        
        for (double x = minX; x <= maxX; x += 1.0) {
            w.spawnParticle(p, x, minY, minZ, 1, 0, 0, 0, 0); w.spawnParticle(p, x, maxY, minZ, 1, 0, 0, 0, 0);
            w.spawnParticle(p, x, minY, maxZ, 1, 0, 0, 0, 0); w.spawnParticle(p, x, maxY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double y = minY; y <= maxY; y += 1.0) {
            w.spawnParticle(p, minX, y, minZ, 1, 0, 0, 0, 0); w.spawnParticle(p, maxX, y, minZ, 1, 0, 0, 0, 0);
            w.spawnParticle(p, minX, y, maxZ, 1, 0, 0, 0, 0); w.spawnParticle(p, maxX, y, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += 1.0) {
            w.spawnParticle(p, minX, minY, z, 1, 0, 0, 0, 0); w.spawnParticle(p, maxX, minY, z, 1, 0, 0, 0, 0);
            w.spawnParticle(p, minX, maxY, z, 1, 0, 0, 0, 0); w.spawnParticle(p, maxX, maxY, z, 1, 0, 0, 0, 0);
        }
    }

    private void playRestoreEffects(Location loc, Material soundMat, boolean isBreaking) {
        if (!plugin.getConfig().getBoolean("block-restore-effects.enabled")) return;
        try {
            Particle p = Particle.valueOf(plugin.getConfig().getString("block-restore-effects.particle", "CLOUD"));
            float vol = (float) plugin.getConfig().getDouble("block-restore-effects.volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("block-restore-effects.pitch", 1.0);
            
            loc.getWorld().spawnParticle(p, loc.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);

            if (plugin.getConfig().getBoolean("block-restore-effects.dynamic-sound", true)) {
                if (soundMat != null && soundMat != Material.AIR) {
                    if (isLiquid(soundMat)) {
                        Sound s = isBreaking ? Sound.ITEM_BUCKET_EMPTY : Sound.ITEM_BUCKET_FILL;
                        loc.getWorld().playSound(loc, s, vol, pitch);
                    } else if (soundMat.isBlock()) {
                        org.bukkit.block.data.BlockData data = Bukkit.createBlockData(soundMat);
                        Sound s = isBreaking ? data.getSoundGroup().getBreakSound() : data.getSoundGroup().getPlaceSound();
                        loc.getWorld().playSound(loc, s, vol, pitch);
                    }
                    return;
                }
            }

            Sound s = Sound.valueOf(plugin.getConfig().getString("block-restore-effects.sound", "BLOCK_STONE_PLACE"));
            loc.getWorld().playSound(loc, s, vol, pitch);
        } catch (Exception ignored) {}
    }

    public void forceRestoreAll() {
        for (RestoreTask task : queue) {
            task.state.update(true, true);
            for (Entity ent : task.loc.getWorld().getNearbyEntities(task.loc.clone().add(0.5, 1.0, 0.5), 0.5, 1.0, 0.5)) {
                if (ent.getType() == EntityType.ENDER_CRYSTAL || ent.getType() == EntityType.FALLING_BLOCK) ent.remove();
            }
        }
        queue.clear();
        pendingLocations.clear();
    }

    private static class RestoreTask implements Comparable<RestoreTask> {
        Location loc; BlockState state; long executeTime;
        RestoreTask(Location loc, BlockState state, long executeTime) {
            this.loc = loc; this.state = state; this.executeTime = executeTime;
        }
        @Override
        public int compareTo(RestoreTask other) { return Long.compare(this.executeTime, other.executeTime); }
    }
}