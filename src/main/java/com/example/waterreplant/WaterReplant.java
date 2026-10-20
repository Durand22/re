package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockShearEntityEvent;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Replants selected mature crops after water washes them away. */
public final class WaterReplant extends JavaPlugin implements Listener {

    private static final String WORLD_GUARD_PLUGIN = "WorldGuard";
    private static final long DEFAULT_CHECK_INTERVAL_TICKS = 5L;
    private static final long SAVE_INTERVAL_TICKS = 100L;

    private final Map<PendingKey, PendingCrop> pending = new HashMap<>();
    private File pendingFile;
    private final Map<CropType, CropSettings> cropSettings = new EnumMap<>(CropType.class);
    private ReplantProtection protection = block -> true;
    private boolean feedbackSounds;
    private int maxPendingCrops;
    private boolean dispenserPickaxes;
    private boolean dispenserTreeFelling;
    private boolean dispenserMobAttacks;
    private boolean dispenserShears;
    private boolean dispenserTreeReplant;
    private boolean pendingDirty;

    @Override
    public void onEnable() {
        pendingFile = new File(getDataFolder(), "pending.yml");
        saveDefaultConfig();
        migrateConfig();
        reloadSettings();
        loadPending();

        Plugin worldGuard = getServer().getPluginManager().getPlugin(WORLD_GUARD_PLUGIN);
        if (getConfig().getBoolean("worldguard-protection", true) && worldGuard != null && worldGuard.isEnabled()) {
            protection = new WorldGuardProtection();
            getLogger().info("WorldGuard region checks enabled.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        long interval = Math.max(1L, getConfig().getLong("check-interval-ticks", DEFAULT_CHECK_INTERVAL_TICKS));
        getServer().getScheduler().runTaskTimer(this, this::checkPending, interval, interval);
        getServer().getScheduler().runTaskTimer(this, this::savePendingIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        getLogger().info("WaterReplant loaded with crops: " + cropSettings);
    }

    @Override
    public void onDisable() {
        savePending();
    }

    private void reloadSettings() {
        cropSettings.clear();
        boolean legacyCost = getConfig().getBoolean("survival-replant-cost", true);
        for (CropType type : CropType.values()) {
            String path = "crops." + type.name();
            if (getConfig().isList("crops")) {
                boolean listed = getConfig().getStringList("crops").stream()
                        .anyMatch(name -> type.name().equalsIgnoreCase(name));
                boolean enabled = listed || type.addedWithExpandedFarmSupport();
                cropSettings.put(type, new CropSettings(enabled, legacyCost));
            } else {
                boolean enabled = getConfig().getBoolean(path + ".enabled", true);
                boolean cost = getConfig().getBoolean(path + ".survival-cost", legacyCost);
                cropSettings.put(type, new CropSettings(enabled, cost));
            }
        }
        feedbackSounds = getConfig().getBoolean("feedback-sounds", true);
        maxPendingCrops = Math.max(0, getConfig().getInt("max-pending-crops", 0));
        dispenserPickaxes = getConfig().getBoolean("dispenser-tools.pickaxe-breaking", true);
        dispenserTreeFelling = getConfig().getBoolean("dispenser-tools.axe-tree-felling", true);
        dispenserMobAttacks = getConfig().getBoolean("dispenser-tools.sword-mob-attacks", true);
        dispenserShears = getConfig().getBoolean("dispenser-tools.shears", true);
        dispenserTreeReplant = getConfig().getBoolean("dispenser-tools.tree-replant", true);
    }

    private void migrateConfig() {
        int configVersion = getConfig().getInt("config-version", 0);
        if (configVersion >= 2) return;
        int oldCap = getConfig().getInt("max-pending-crops", 0);
        if (oldCap == 0 || oldCap == 256) {
            getConfig().set("max-pending-crops", 0);
        }
        getConfig().set("config-version", 2);
        saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenserToolUse(BlockDispenseEvent event) {
        DispenserTools.handle(event, protection, dispenserPickaxes, dispenserTreeFelling,
                dispenserMobAttacks, dispenserTreeReplant);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenserShear(BlockShearEntityEvent event) {
        if (!dispenserShears || !protection.mayReplant(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.WATER) {
            return;
        }

        Block target = event.getToBlock();
        CropType crop = CropType.forCrop(target.getType());
        CropSettings settings = crop == null ? null : cropSettings.get(crop);
        if (settings == null || !settings.enabled()) {
            return;
        }

        if (crop == CropType.PUMPKIN || crop == CropType.MELON) {
            if (protection.mayReplant(target)) {
                target.breakNaturally(true);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        if (crop.regrowsFromBase()) {
            if (target.getRelative(BlockFace.DOWN).getType() != crop.crop()) {
                event.setCancelled(true);
                return;
            }
        } else if (crop.needsAgeToHarvest()) {
            if (!(target.getBlockData() instanceof Ageable ageable)) return;
            if (!CropType.isMature(ageable.getAge(), ageable.getMaximumAge())) {
                event.setCancelled(true);
                return;
            }
        }

        PendingKey key = PendingKey.of(target);
        if (!pending.containsKey(key)) {
            if (maxPendingCrops > 0 && pending.size() >= maxPendingCrops) {
                event.setCancelled(true);
                return;
            }
            boolean costsItem = crop.replantsAfterHarvest() && settings.survivalCost();
            pending.put(key, new PendingCrop(crop, !costsItem));
            pendingDirty = true;
        }
    }

    /** Pay the survival replant cost from the actual vanilla harvest drops. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakByLiquid(BlockBreakBlockEvent event) {
        PendingKey key = PendingKey.of(event.getBlock());
        PendingCrop pendingCrop = pending.get(key);
        if (pendingCrop == null) {
            return;
        }

        if (feedbackSounds) {
            event.getBlock().getWorld().playSound(event.getBlock().getLocation(), Sound.BLOCK_CROP_BREAK, 0.35f, 1.0f);
        }
        if (pendingCrop.seedPaid()) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        List<CropDrop> dropTypes = drops.stream()
                .map(drop -> new CropDrop(drop.getType().name(), drop.getAmount()))
                .toList();
        int seedIndex = pendingCrop.type().seedDropIndex(dropTypes);
        if (seedIndex >= 0) {
            ItemStack seedStack = drops.get(seedIndex);
            if (seedStack.getAmount() == 1) {
                drops.remove(seedIndex);
            } else {
                seedStack.setAmount(seedStack.getAmount() - 1);
            }
            pending.put(key, new PendingCrop(pendingCrop.type(), true));
            pendingDirty = true;
        }
    }

    private void checkPending() {
        Iterator<Map.Entry<PendingKey, PendingCrop>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PendingKey, PendingCrop> entry = iterator.next();
            PendingKey key = entry.getKey();
            World world = getServer().getWorld(key.worldId());
            if (world == null) {
                continue;
            }

            int chunkX = key.x() >> 4;
            int chunkZ = key.z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            Material current = block.getType();
            if (current == Material.WATER) {
                continue;
            }

            PendingCrop crop = entry.getValue();
            boolean correctSoil = crop.type().hasCorrectSoil(block.getRelative(BlockFace.DOWN).getType());
            boolean eligible = ReplantRules.mayReplant(current.isAir(), current == Material.WATER,
                    crop.seedPaid(), correctSoil);
            if (eligible && crop.type().replantsAfterHarvest() && protection.mayReplant(block)) {
                BlockData fresh = crop.type().crop().createBlockData();
                if (fresh instanceof Ageable ageable) {
                    ageable.setAge(0);
                    block.setBlockData(fresh);
                    if (feedbackSounds) {
                        world.playSound(block.getLocation(), Sound.ITEM_CROP_PLANT, 0.35f, 1.0f);
                    }
                }
            }

            iterator.remove();
            pendingDirty = true;
        }
    }

    private void loadPending() {
        if (!pendingFile.isFile()) {
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(pendingFile);
        for (Map<?, ?> row : data.getMapList("pending")) {
            try {
                UUID worldId = UUID.fromString(String.valueOf(row.get("world")));
                int x = Integer.parseInt(String.valueOf(row.get("x")));
                int y = Integer.parseInt(String.valueOf(row.get("y")));
                int z = Integer.parseInt(String.valueOf(row.get("z")));
                CropType crop = CropType.fromConfigName(String.valueOf(row.get("crop")));
                boolean seedPaid = Boolean.parseBoolean(String.valueOf(row.get("seed-paid")));
                if (crop != null) {
                    boolean survivalCost = cropSettings.getOrDefault(crop, new CropSettings(false, true)).survivalCost();
                    pending.put(new PendingKey(worldId, x, y, z), new PendingCrop(crop, seedPaid || !survivalCost));
                }
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Skipping invalid entry in pending.yml: " + row);
            }
        }
    }

    private void savePendingIfDirty() {
        if (pendingDirty) {
            savePending();
        }
    }

    private void savePending() {
        YamlConfiguration data = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>(pending.size());
        for (Map.Entry<PendingKey, PendingCrop> entry : pending.entrySet()) {
            PendingKey key = entry.getKey();
            PendingCrop crop = entry.getValue();
            Map<String, Object> row = new HashMap<>();
            row.put("world", key.worldId().toString());
            row.put("x", key.x());
            row.put("y", key.y());
            row.put("z", key.z());
            row.put("crop", crop.type().name());
            row.put("seed-paid", crop.seedPaid());
            rows.add(row);
        }
        data.set("pending", rows);
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            data.save(pendingFile);
            pendingDirty = false;
        } catch (IOException exception) {
            getLogger().severe("Could not save pending crop data: " + exception.getMessage());
        }
    }

    private record PendingKey(UUID worldId, int x, int y, int z) {
        private static PendingKey of(Block block) {
            return new PendingKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record PendingCrop(CropType type, boolean seedPaid) {}

    private record CropSettings(boolean enabled, boolean survivalCost) {}
}
