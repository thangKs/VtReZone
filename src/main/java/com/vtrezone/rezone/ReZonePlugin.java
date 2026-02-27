/*
 * VtReZone- Automatically resets a region to its original state.
 * Copyright (c) 2026 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */
package com.vtrezone.rezone;

import com.vtrezone.rezone.managers.RegenManager;
import com.vtrezone.rezone.managers.RegionManager;
import com.vtrezone.rezone.models.Region;
import com.vtrezone.rezone.utils.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReZonePlugin extends JavaPlugin implements Listener {

    private RegionManager regionManager;
    private RegenManager regenManager;
    
    private final Map<UUID, Location> wandPos1 = new HashMap<>();
    private final Map<UUID, Location> wandPos2 = new HashMap<>();
    
    private final Map<UUID, String> chatAction = new HashMap<>(); 

    @Override
    public void onEnable() {
        saveDefaultConfig();
        regionManager = new RegionManager(this);
        regenManager = new RegenManager(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(regenManager, this);
        getLogger().info("ReZone has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (regenManager != null) regenManager.forceRestoreAll();
    }

    public RegionManager getRegionManager() { return regionManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rezone.admin")) return true;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== ReZone Commands ===");
            sender.sendMessage(ChatColor.AQUA + "/rz wand" + ChatColor.WHITE + " - Get the selection wand");
            sender.sendMessage(ChatColor.AQUA + "/rz create <name>" + ChatColor.WHITE + " - Create a new region");
            sender.sendMessage(ChatColor.AQUA + "/rz delete <name>" + ChatColor.WHITE + " - Delete a region");
            sender.sendMessage(ChatColor.AQUA + "/rz list" + ChatColor.WHITE + " - List all regions");
            sender.sendMessage(ChatColor.AQUA + "/rz import <wg_name> <rz_name>" + ChatColor.WHITE + " - Import from WorldGuard");
            sender.sendMessage(ChatColor.AQUA + "/rz update <name>" + ChatColor.WHITE + " - Update region memory");
            sender.sendMessage(ChatColor.AQUA + "/rz menu <name>" + ChatColor.WHITE + " - Open settings menu");
            sender.sendMessage(ChatColor.AQUA + "/rz reload" + ChatColor.WHITE + " - Reload configuration");
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;

        switch (args[0].toLowerCase()) {
            case "list":
                sender.sendMessage(ChatColor.GOLD + "=== Region List (" + regionManager.getAllRegions().size() + ") ===");
                for (Region r : regionManager.getAllRegions()) {
                    sender.sendMessage(ChatColor.AQUA + "- " + r.getId() + ChatColor.GRAY + " (Delay: " + r.getDelay() + "s)");
                }
                break;

            case "delete":
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rz delete <name>");
                    return true;
                }
                if (regionManager.removeRegion(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Successfully deleted region: " + args[1]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Region not found: " + args[1]);
                }
                break;

            case "wand":
                if (player != null) giveWand(player);
                break;

            case "create":
                if (player == null || args.length < 2) return false;
                if (wandPos1.containsKey(player.getUniqueId()) && wandPos2.containsKey(player.getUniqueId())) {
                    regionManager.addRegion(args[1], wandPos1.get(player.getUniqueId()), wandPos2.get(player.getUniqueId()));
                    player.sendMessage(ChatColor.GREEN + "Successfully created region: " + args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "You haven't selected 2 points (Use /rz wand).");
                }
                break;
                
            case "import":
                if (player == null || args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rz import <wg_name> <rz_name>");
                    return true;
                }
                if (WorldGuardHook.hasWorldGuard()) {
                    Location[] wgPoints = WorldGuardHook.getSavedRegion(player.getWorld(), args[1]);
                    if (wgPoints != null) {
                        regionManager.addRegion(args[2], wgPoints[0], wgPoints[1]);
                        player.sendMessage(ChatColor.GREEN + "Successfully imported WorldGuard region '" + args[1] + "' as ReZone '" + args[2] + "'!");
                    } else {
                        player.sendMessage(ChatColor.RED + "WorldGuard region not found: " + args[1]);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "WorldGuard is not installed on this server!");
                }
                break;

            case "update":
                if (args.length < 2) return false;
                Region targetRegion = regionManager.getRegion(args[1]);
                if (targetRegion != null) {
                    regenManager.updateRegionMemory(targetRegion);
                    sender.sendMessage(ChatColor.GREEN + "Successfully updated memory for region '" + args[1] + "'!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Region not found!");
                }
                break;

            case "menu":
                if (player == null || args.length < 2) return false;
                Region menuRegion = regionManager.getRegion(args[1]);
                if (menuRegion != null) openMainMenu(player, menuRegion);
                else player.sendMessage(ChatColor.RED + "Region not found!");
                break;

            case "reload":
                regenManager.forceRestoreAll();
                reloadConfig();
                regionManager.loadRegionsFile();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded and pending blocks restored!");
                break;
        }
        return true;
    }

    private void giveWand(Player player) {
        String matName = getConfig().getString("wand.item", "BLAZE_ROD");
        ItemStack wand = new ItemStack(Material.valueOf(matName));
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("wand.name")));
        List<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("wand.lore")) lore.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, glass);
            }
        }
    }

    public void openMainMenu(Player player, Region region) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "ReZone: " + region.getId());
        inv.setItem(10, createItem(Material.CLOCK, ChatColor.YELLOW + "Default Delay", ChatColor.GRAY + "Current: " + ChatColor.AQUA + region.getDelay() + "s", "", ChatColor.GREEN + "Click to customize"));
        inv.setItem(12, createItem(Material.HOPPER, ChatColor.GREEN + "Block Filter", ChatColor.GRAY + "Mode: " + ChatColor.AQUA + region.getFilterType(), "", ChatColor.GREEN + "Click to edit list"));
        inv.setItem(14, createItem(Material.DIAMOND_ORE, ChatColor.AQUA + "Custom Delays", ChatColor.GRAY + "Contains: " + ChatColor.AQUA + region.getCustomDelays().size() + " blocks", "", ChatColor.GREEN + "Click to configure"));
        inv.setItem(16, createItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Update Memory", ChatColor.GRAY + "Save the latest state.", "", ChatColor.GREEN + "Click to update"));
        
        String modeName = region.isBatchMode() ? ChatColor.LIGHT_PURPLE + "BATCH" : ChatColor.AQUA + "INDIVIDUAL";
        inv.setItem(20, createItem(Material.REPEATER, ChatColor.GOLD + "Reset Mode", ChatColor.GRAY + "Current: " + modeName, ChatColor.GRAY + "Toggle how blocks reset.", "", ChatColor.YELLOW + "Click to toggle"));

        inv.setItem(22, createItem(Material.BEACON, ChatColor.GOLD + "Instant Reset", ChatColor.GRAY + "Force restore all pending blocks", ChatColor.GRAY + "in this region NOW.", "", ChatColor.YELLOW + "Click to force reset"));
        
        inv.setItem(26, createItem(Material.BARRIER, ChatColor.RED + "Delete Region", ChatColor.GRAY + "Permanently delete this region.", "", ChatColor.DARK_RED + "Shift + Left-Click to Delete"));
        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openTimeMenu(Player player, Region region) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Default Delay: " + region.getId());
        
        inv.setItem(9, createItem(Material.RED_CONCRETE, ChatColor.RED + "-60s"));
        inv.setItem(10, createItem(Material.RED_CONCRETE, ChatColor.RED + "-30s"));
        inv.setItem(11, createItem(Material.RED_CONCRETE, ChatColor.RED + "-5s"));
        inv.setItem(12, createItem(Material.RED_CONCRETE, ChatColor.RED + "-1s"));
        
        inv.setItem(13, createItem(Material.CLOCK, ChatColor.YELLOW + "Current: " + region.getDelay() + "s"));
        
        inv.setItem(14, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+1s"));
        inv.setItem(15, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+5s"));
        inv.setItem(16, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+30s"));
        inv.setItem(17, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+60s"));
        
        inv.setItem(22, createItem(Material.ARROW, ChatColor.WHITE + "Go Back"));
        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openFilterMenu(Player player, Region region) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Filter: " + region.getId());
        
        int slot = 0;
        for (String block : region.getFilteredBlocks()) {
            if (slot > 44) break;
            Material m = Material.matchMaterial(block);
            inv.setItem(slot++, createItem(m, ChatColor.YELLOW + block, ChatColor.RED + "Press Q (Drop) to remove"));
        }
        
        inv.setItem(45, createItem(Material.HOPPER, ChatColor.GREEN + "Change Filter Mode", ChatColor.GRAY + "Current: " + ChatColor.AQUA + region.getFilterType(), "", ChatColor.YELLOW + "Click to switch"));
        inv.setItem(49, createItem(Material.EMERALD, ChatColor.GREEN + "Add Block", ChatColor.GRAY + "Click to enter block name"));
        inv.setItem(53, createItem(Material.ARROW, ChatColor.WHITE + "Go Back"));
        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openCustomDelayMenu(Player player, Region region) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.BLUE + "Custom Delays: " + region.getId());
        
        int slot = 0;
        for (Map.Entry<String, Integer> entry : region.getCustomDelays().entrySet()) {
            if (slot > 44) break;
            Material m = Material.matchMaterial(entry.getKey());
            inv.setItem(slot++, createItem(m, ChatColor.YELLOW + entry.getKey(), 
                ChatColor.AQUA + "Delay: " + entry.getValue() + "s", 
                "",
                ChatColor.GREEN + "Click to adjust time",
                ChatColor.RED + "Press Q (Drop) to remove"));
        }
        
        inv.setItem(49, createItem(Material.EMERALD, ChatColor.GREEN + "Add Block", ChatColor.GRAY + "Click to enter block name"));
        inv.setItem(53, createItem(Material.ARROW, ChatColor.WHITE + "Go Back"));
        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openSingleBlockDelayMenu(Player player, Region region, String blockName) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Delay: " + region.getId() + " | " + blockName);
        
        inv.setItem(9, createItem(Material.RED_CONCRETE, ChatColor.RED + "-60s"));
        inv.setItem(10, createItem(Material.RED_CONCRETE, ChatColor.RED + "-30s"));
        inv.setItem(11, createItem(Material.RED_CONCRETE, ChatColor.RED + "-5s"));
        inv.setItem(12, createItem(Material.RED_CONCRETE, ChatColor.RED + "-1s"));
        
        int currentDelay = region.getCustomDelays().getOrDefault(blockName, region.getDelay());
        Material m = Material.matchMaterial(blockName);
        
        inv.setItem(13, createItem(m, ChatColor.YELLOW + blockName, ChatColor.AQUA + "Current: " + currentDelay + "s"));
        
        inv.setItem(14, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+1s"));
        inv.setItem(15, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+5s"));
        inv.setItem(16, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+30s"));
        inv.setItem(17, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "+60s"));
        
        inv.setItem(22, createItem(Material.ARROW, ChatColor.WHITE + "Go Back"));
        fillBackground(inv);
        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        if (mat == null) mat = Material.BEDROCK;
        
        try {
            if (!mat.isItem()) {
                String n = mat.name();
                if (n.contains("WATER")) mat = Material.WATER_BUCKET;
                else if (n.contains("LAVA")) mat = Material.LAVA_BUCKET;
                else if (n.contains("FIRE")) mat = Material.CAMPFIRE;
                else if (n.contains("PORTAL")) mat = Material.OBSIDIAN;
                else if (n.contains("STEM") || n.contains("CROPS") || n.equals("WHEAT")) mat = Material.WHEAT;
                else if (n.contains("WALL")) mat = Material.COBBLESTONE_WALL;
                else if (n.contains("PISTON_HEAD")) mat = Material.PISTON;
                else mat = Material.BEDROCK;
            }
        } catch (Exception ignored) {
            mat = Material.BEDROCK;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        if (title.startsWith("ReZone: ") || title.startsWith("Default Delay: ") || 
            title.startsWith("Filter: ") || title.startsWith("Custom Delays: ") || title.startsWith("Delay: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            
            int slot = e.getRawSlot();
            
            if (title.startsWith("Delay: ")) {
                String[] parts = title.replace("Delay: ", "").split(" \\| ");
                if (parts.length < 2) return;
                String regionId = parts[0];
                String blockName = parts[1];
                
                Region region = regionManager.getRegion(regionId);
                if (region == null) { p.closeInventory(); return; }

                if (slot == 22) { openCustomDelayMenu(p, region); return; }

                int current = region.getCustomDelays().getOrDefault(blockName, region.getDelay());
                
                if (slot == 9) current = Math.max(1, current - 60);
                if (slot == 10) current = Math.max(1, current - 30);
                if (slot == 11) current = Math.max(1, current - 5);
                if (slot == 12) current = Math.max(1, current - 1);
                if (slot == 14) current += 1;
                if (slot == 15) current += 5;
                if (slot == 16) current += 30;
                if (slot == 17) current += 60;
                
                if (slot >= 9 && slot <= 17) {
                    region.getCustomDelays().put(blockName, current);
                    regionManager.saveRegion(region);
                    openSingleBlockDelayMenu(p, region, blockName);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                return;
            }

            String regionId = title.split(": ")[1];
            Region region = regionManager.getRegion(regionId);
            if (region == null) { p.closeInventory(); return; }
            
            if (title.startsWith("ReZone: ")) {
                if (slot == 10) openTimeMenu(p, region);
                else if (slot == 12) openFilterMenu(p, region);
                else if (slot == 14) openCustomDelayMenu(p, region);
                else if (slot == 16) {
                    p.closeInventory();
                    regenManager.updateRegionMemory(region);
                    p.sendMessage(ChatColor.GREEN + "Successfully updated memory for region '" + region.getId() + "'!");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
                else if (slot == 20) {
                    region.setBatchMode(!region.isBatchMode());
                    regionManager.saveRegion(region);
                    openMainMenu(p, region);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
                else if (slot == 22) {
                    p.closeInventory();
                    regenManager.forceRestoreRegion(region);
                    p.sendMessage(ChatColor.GREEN + "Forced immediate reset for region '" + region.getId() + "'!");
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                }
                else if (slot == 26 && e.getClick() == ClickType.SHIFT_LEFT) {
                    p.closeInventory();
                    regionManager.removeRegion(region.getId());
                    p.sendMessage(ChatColor.GREEN + "Successfully deleted region: " + region.getId());
                    p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                }
                
                if (slot == 10 || slot == 12 || slot == 14 || slot == 16 || slot == 20 || slot == 26) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
            
            else if (title.startsWith("Default Delay: ")) {
                if (slot == 22) { openMainMenu(p, region); return; }
                
                int current = region.getDelay();
                if (slot == 9) current = Math.max(1, current - 60);
                if (slot == 10) current = Math.max(1, current - 30);
                if (slot == 11) current = Math.max(1, current - 5);
                if (slot == 12) current = Math.max(1, current - 1);
                if (slot == 14) current += 1;
                if (slot == 15) current += 5;
                if (slot == 16) current += 30;
                if (slot == 17) current += 60;
                
                if (slot >= 9 && slot <= 17) {
                    region.setDelay(current);
                    regionManager.saveRegion(region);
                    openTimeMenu(p, region);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
            }
            
            else if (title.startsWith("Filter: ")) {
                if (slot == 53) { openMainMenu(p, region); return; }
                if (slot == 45) { 
                    String f = region.getFilterType();
                    if (f.equals("NONE")) region.setFilterType("WHITELIST");
                    else if (f.equals("WHITELIST")) region.setFilterType("BLACKLIST");
                    else region.setFilterType("NONE");
                    regionManager.saveRegion(region);
                    openFilterMenu(p, region);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
                else if (slot == 49) { 
                    p.closeInventory();
                    chatAction.put(p.getUniqueId(), "FILTER_" + region.getId());
                    p.sendMessage(ChatColor.YELLOW + "Please type the BLOCK NAME in chat (e.g. STONE). Type 'cancel' to abort.");
                }
                else if (slot >= 0 && slot <= 44) {
                    if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE) {
                        if (e.getClick() == ClickType.DROP) {
                            String blockName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                            region.getFilteredBlocks().remove(blockName);
                            regionManager.saveRegion(region);
                            openFilterMenu(p, region);
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                        }
                    }
                }
            }
            
            else if (title.startsWith("Custom Delays: ")) {
                if (slot == 53) { openMainMenu(p, region); return; }
                if (slot == 49) {
                    p.closeInventory();
                    chatAction.put(p.getUniqueId(), "DELAY_" + region.getId());
                    p.sendMessage(ChatColor.YELLOW + "Please type the BLOCK NAME in chat (e.g. OBSIDIAN). Type 'cancel' to abort.");
                }
                else if (slot >= 0 && slot <= 44) {
                    if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE) {
                        String blockName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                        if (e.getClick() == ClickType.DROP) {
                            region.getCustomDelays().remove(blockName);
                            regionManager.saveRegion(region);
                            openCustomDelayMenu(p, region);
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                        } else {
                            openSingleBlockDelayMenu(p, region, blockName);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!chatAction.containsKey(p.getUniqueId())) return;
        
        e.setCancelled(true);
        String action = chatAction.get(p.getUniqueId());
        String msg = e.getMessage().toUpperCase().trim();
        chatAction.remove(p.getUniqueId());

        if (msg.equals("CANCEL")) {
            p.sendMessage(ChatColor.RED + "Action cancelled.");
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (action.startsWith("FILTER_")) {
                String rId = action.split("_")[1];
                Region r = regionManager.getRegion(rId);
                if (r != null) {
                    Material m = Material.matchMaterial(msg);
                    if (m != null) {
                        if (!r.getFilteredBlocks().contains(m.name())) r.getFilteredBlocks().add(m.name());
                        regionManager.saveRegion(r);
                        p.sendMessage(ChatColor.GREEN + "Added " + m.name() + " to the filter list.");
                        openFilterMenu(p, r);
                    } else {
                        p.sendMessage(ChatColor.RED + "Invalid block name!");
                    }
                }
            } 
            else if (action.startsWith("DELAY_")) {
                String rId = action.split("_")[1];
                Region r = regionManager.getRegion(rId);
                if (r != null) {
                    Material m = Material.matchMaterial(msg);
                    if (m != null) {
                        r.getCustomDelays().put(m.name(), r.getDelay());
                        regionManager.saveRegion(r);
                        p.sendMessage(ChatColor.GREEN + "Added block " + m.name() + ". Please adjust its time in the menu.");
                        openSingleBlockDelayMenu(p, r, m.name());
                    } else {
                        p.sendMessage(ChatColor.RED + "Invalid block name!");
                    }
                }
            }
        });
    }

    @EventHandler
    public void onWandInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getItem() == null || !e.getItem().hasItemMeta() || !e.getItem().getItemMeta().hasDisplayName()) return;
        String wandName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("wand.name", ""));
        if (!e.getItem().getItemMeta().getDisplayName().equals(wandName)) return;

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            wandPos1.put(player.getUniqueId(), e.getClickedBlock().getLocation());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Position 1 selected!");
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            wandPos2.put(player.getUniqueId(), e.getClickedBlock().getLocation());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Position 2 selected!");
            e.setCancelled(true);
        }
    }
}