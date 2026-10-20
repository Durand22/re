package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Survival-balanced, redstone-triggered tool actions for dispensers. */
final class DispenserTools {
    private static final int MAX_TREE_LOGS = 64;
    private static final int MAX_TREE_RADIUS = 6;
    private static final double MOB_DAMAGE = 6.0;
    private static final double TARGET_RADIUS_SQUARED = 2.25;

    private DispenserTools() {}

    static boolean handle(BlockDispenseEvent event, ReplantProtection protection,
                          boolean blockBreaking, boolean treeFelling, boolean mobAttacking,
                          boolean treeReplanting) {
        ItemStack dispensed = event.getItem();
        Material material = dispensed.getType();
        boolean pickaxe = material.name().endsWith("_PICKAXE");
        boolean axe = material.name().endsWith("_AXE") && !material.name().endsWith("_PICKAXE");
        boolean sword = material.name().endsWith("_SWORD");
        if (!(blockBreaking && pickaxe) && !(treeFelling && axe) && !(mobAttacking && sword)) {
            return false;
        }

        event.setCancelled(true);
        if (!(event.getBlock().getState() instanceof Dispenser dispenser)
                || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return true;
        }

        int slot = findToolSlot(dispenser, dispensed);
        if (slot < 0) return true;
        ItemStack tool = dispenser.getInventory().getItem(slot);
        if (tool == null || tool.getType().isAir()) return true;
        Block target = event.getBlock().getRelative(directional.getFacing());
        Material targetType = target.getType();

        int uses = 0;
        if (pickaxe && blockBreaking) {
            if (protection.mayReplant(target) && Tag.MINEABLE_PICKAXE.isTagged(target.getType())
                    && target.isPreferredTool(tool) && target.breakNaturally(tool, true, true)) {
                uses = 1;
            }
        } else if (axe && treeFelling) {
            TreeResult tree = fellTree(target, tool, protection);
            uses = tree.logsBroken();
            if (uses > 0 && treeReplanting && tree.root() != null) {
                plantSapling(dispenser, tree.root(), startSapling(targetType), protection);
            }
        } else if (sword && mobAttacking) {
            LivingEntity victim = nearestHostile(target, directional.getFacing());
            if (victim != null && protection.mayReplant(victim.getLocation().getBlock())) {
                victim.damage(MOB_DAMAGE);
                uses = 1;
            }
        }

        if (uses > 0) {
            damageTool(tool, uses);
            dispenser.getInventory().setItem(slot, tool.getAmount() <= 0 ? null : tool);
        }
        return true;
    }

    private static TreeResult fellTree(Block start, ItemStack tool, ReplantProtection protection) {
        if (!Tag.LOGS.isTagged(start.getType()) || !Tag.MINEABLE_AXE.isTagged(start.getType())
                || !protection.mayReplant(start)) return new TreeResult(0, null);

        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        int broken = 0;
        int maxUses = Math.min(MAX_TREE_LOGS, remainingDurability(tool));
        Block root = null;
        while (!queue.isEmpty() && broken < maxUses) {
            Block current = queue.removeFirst();
            String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key) || Math.abs(current.getX() - start.getX()) > MAX_TREE_RADIUS
                    || Math.abs(current.getZ() - start.getZ()) > MAX_TREE_RADIUS
                    || current.getY() < start.getY() - 1 || current.getY() > start.getY() + 16
                    || !Tag.LOGS.isTagged(current.getType()) || !Tag.MINEABLE_AXE.isTagged(current.getType())
                    || !protection.mayReplant(current)) continue;

            if (isSaplingSoil(current.getRelative(BlockFace.DOWN).getType())
                    && (root == null || current.getY() < root.getY())) root = current;
            if (current.breakNaturally(tool, true, true)) broken++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            queue.addLast(current.getRelative(dx, dy, dz));
                        }
                    }
                }
            }
        }
        return new TreeResult(broken, root);
    }

    private static void plantSapling(Dispenser dispenser, Block root, Material sapling,
                                     ReplantProtection protection) {
        if (sapling == null || !root.getType().isAir()
                || !isSaplingSoil(root.getRelative(BlockFace.DOWN).getType())
                || !protection.mayReplant(root)) return;
        int slot = findToolSlot(dispenser, new ItemStack(sapling));
        if (slot < 0) return;
        ItemStack supplied = dispenser.getInventory().getItem(slot);
        if (supplied == null || supplied.getAmount() < 1) return;
        root.setType(sapling, true);
        if (supplied.getAmount() == 1) dispenser.getInventory().setItem(slot, null);
        else supplied.setAmount(supplied.getAmount() - 1);
    }

    private static Material startSapling(Material log) {
        String name = log.name().replaceFirst("^STRIPPED_", "");
        String tree = name.replaceFirst("_(LOG|WOOD)$", "");
        if (tree.equals("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        return Material.matchMaterial(tree + "_SAPLING");
    }

    private static boolean isSaplingSoil(Material material) {
        return switch (material) {
            case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, MOSS_BLOCK, MYCELIUM -> true;
            default -> false;
        };
    }

    private static LivingEntity nearestHostile(Block target, BlockFace facing) {
        var center = target.getLocation().add(0.5, 0.5, 0.5).add(facing.getDirection().multiply(0.5));
        LivingEntity nearest = null;
        double nearestDistance = TARGET_RADIUS_SQUARED;
        for (Entity entity : target.getWorld().getNearbyEntities(center, 1.5, 1.5, 1.5)) {
            if (!(entity instanceof Monster monster) || !monster.isValid() || monster.isDead()) continue;
            double distance = entity.getLocation().distanceSquared(center);
            if (distance < nearestDistance) {
                nearest = monster;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static int findToolSlot(Dispenser dispenser, ItemStack dispensed) {
        for (int slot = 0; slot < dispenser.getInventory().getSize(); slot++) {
            ItemStack stored = dispenser.getInventory().getItem(slot);
            if (stored != null && stored.isSimilar(dispensed)) return slot;
        }
        return -1;
    }

    private static void damageTool(ItemStack tool, int damage) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        int maximum = tool.getType().getMaxDurability();
        if (maximum <= 0) return;
        int newDamage = damageable.getDamage() + damage;
        if (newDamage >= maximum) {
            tool.setAmount(0);
            return;
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
    }

    private static int remainingDurability(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable damageable)) return MAX_TREE_LOGS;
        return Math.max(0, tool.getType().getMaxDurability() - damageable.getDamage());
    }

    private record TreeResult(int logsBroken, Block root) {}
}
