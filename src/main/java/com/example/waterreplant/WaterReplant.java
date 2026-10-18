package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class WaterReplant extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WaterReplant has successfully loaded!");
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block targetBlock = event.getToBlock();

        // Check if water is flowing onto a crop
        if (event.getBlock().getType() == Material.WATER && isCrop(targetBlock.getType())) {
            if (targetBlock.getBlockData() instanceof Ageable ageable) {
                // Check if the crop is fully grown
                if (ageable.getAge() == ageable.getMaximumAge()) {
                    Material cropType = targetBlock.getType();

                    // Delay the break and replant slightly to prevent physics crashes
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            targetBlock.breakNaturally();
                            targetBlock.setType(cropType);
                            
                            if (targetBlock.getBlockData() instanceof Ageable freshAgeable) {
                                freshAgeable.setAge(0);
                                targetBlock.setBlockData(freshAgeable);
                            }
                        }
                    }.runTask(this);
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