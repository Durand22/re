package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class WaterReplant extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WaterReplant has successfully loaded!");
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();

        if (isCrop(block.getType())) {
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() == ageable.getMaximumAge()) {

                    if (block.getRelative(0, 1, 0).getType() == Material.WATER) {
                        Material cropType = block.getType();
                        block.breakNaturally();

                        getServer().getScheduler().runTaskLater(this, () -> {
                            block.setType(cropType);
                        }, 2L);
                    }
                }
            }
        }
    }

    private boolean isCrop(Material type) {
        return type == Material.WHEAT || 
               type == Material.CARROTS || 
               type == Material.POTATOES || 
               type == Material.BEETROOTS;
    }
}