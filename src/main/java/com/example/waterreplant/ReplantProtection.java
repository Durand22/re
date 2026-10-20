package com.example.waterreplant;

import org.bukkit.block.Block;

@FunctionalInterface
interface ReplantProtection {
    boolean mayReplant(Block block);
}
