🌐 VtReZone

VtReZone is an advanced, highly optimized Minecraft region regeneration plugin for Spigot/Paper (1.16.5+). It automatically tracks block changes within defined regions and restores them to their original state after a customizable delay.

Perfect for minigames, PVP arenas, and resource-gathering zones!

✨ Features

Smart Liquid Sweeper: Employs an advanced BFS (Breadth-First Search) algorithm to completely eradicate infinite water/lava sources and lingering flowing liquids.

Dynamic Sound Engine: Automatically detects and plays the correct place/break sounds for restored materials (e.g., Glass shattering, Wood placing).

Interactive GUI: Manage everything seamlessly through a beautiful, in-game glass-paneled menu.

Per-Block Delay: Set custom restoration timers for specific blocks (e.g., Obsidian takes 10 minutes, Dirt takes 10 seconds).

Blacklist / Whitelist Filters: Control exactly which blocks the plugin should ignore or track.

WorldGuard Integration: Easily import your existing WorldGuard regions into ReZone.

🚀 Commands

Command

Description

/rz wand

Get the region selection wand.

/rz create <name>

Create a new auto-regenerating region.

/rz delete <name>

Delete an existing region.

/rz list

View all active regions.

/rz menu <name>

Open the interactive GUI for a region.

/rz update <name>

Update the memory (save the current state of the region).

/rz import <wg_name> <rz_name>

Import a region from WorldGuard.

/rz reload

Instantly restore all pending blocks and reload configs.

🛡️ Permissions

rezone.admin - Grants access to all ReZone commands.

⚙️ Configuration

The config.yml provides various settings for performance optimization, visual particle borders, and dynamic sound toggling.

📄 License

This project is licensed under the MIT License.