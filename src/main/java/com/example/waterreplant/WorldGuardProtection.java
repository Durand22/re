package com.example.waterreplant;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.block.Block;

/** Loaded only when WorldGuard is installed and enabled. */
final class WorldGuardProtection implements ReplantProtection {
    private final RegionQuery query = WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .createQuery();

    @Override
    public boolean mayReplant(Block block) {
        return query.testState(BukkitAdapter.adapt(block.getLocation()), null, Flags.BUILD);
    }
}
