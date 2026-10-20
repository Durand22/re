package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Water (from a bucket or a dispenser) washes away fully grown crops and drops
 * the harvest. Once the water is gone again (bucket picked up, dispenser
 * emptied, flow dried up) the crop is replanted at age 0.
 *
 * Works for Bedrock players through Geyser too: everything is server-side.
 */
public final class WaterReplant extends JavaPlugin implements Listener {

    /** Crop cells that water destroyed and that still need replanting. */
    private final Map<Block, Material> pending = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        // Every 5 ticks, see whether any washed-out crop cell is free of water again.
        getServer().getScheduler().runTaskTimer(this, this::checkPending, 5L, 5L);
        getLogger().info("WaterReplant has successfully loaded!");
    }

    @Override
    public void onDisable() {
        pending.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // Only water flowing (a bucket or dispenser source spreads like this)
        if (event.getBlock().getType() != Material.WATER) {
            return;
        }

        Block target = event.getToBlock();
        Material type = target.getType();
        if (!isCrop(type)) {
            return;
        }

        // Only fully grown crops count as a harvest
        if (!(target.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        // Vanilla water destroys the crop and drops the items itself.
        // We only remember the spot so we can replant it later.
        pending.putIfAbsent(target, type);
    }

    private void checkPending() {
        Iterator<Map.Entry<Block, Material>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, Material> entry = it.next();
            Block block = entry.getKey();

            // Don't touch unloaded chunks; try again once loaded
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }

            Material current = block.getType();

            if (current == Material.WATER) {
                continue; // still flooded, keep waiting
            }

            if (current.isAir() && block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND) {
                BlockData fresh = entry.getValue().createBlockData();
                if (fresh instanceof Ageable ageable) {
                    ageable.setAge(0);
                }
                block.setBlockData(fresh);
            }

            // Replanted, or the spot is now something else (or farmland is gone): done
            it.remove();
        }
    }

    private boolean isCrop(Material type) {
        return type == Material.WHEAT
                || type == Material.CARROTS
                || type == Material.POTATOES
                || type == Material.BEETROOTS;
    }
}
