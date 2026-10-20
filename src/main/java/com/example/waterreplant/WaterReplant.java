package com.example.waterreplant;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Replants selected mature crops after water washes them away. */
public final class WaterReplant extends JavaPlugin implements Listener {

    private static final String WORLD_GUARD_PLUGIN = "WorldGuard";
    private static final long DEFAULT_CHECK_INTERVAL_TICKS = 5L;
    private static final long SAVE_INTERVAL_TICKS = 100L;

    private final Map<PendingKey, PendingCrop> pending = new HashMap<>();
    private File pendingFile;
    private Set<CropType> enabledCrops = EnumSet.noneOf(CropType.class);
    private ReplantProtection protection = block -> true;
    private boolean survivalCost;
    private boolean pendingDirty;

    @Override
    public void onEnable() {
        pendingFile = new File(getDataFolder(), "pending.yml");
        saveDefaultConfig();
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
        getLogger().info("WaterReplant loaded with crops: " + enabledCrops);
    }

    @Override
    public void onDisable() {
        savePending();
    }

    private void reloadSettings() {
        enabledCrops = EnumSet.noneOf(CropType.class);
        for (String configured : getConfig().getStringList("crops")) {
            CropType type = CropType.fromConfigName(configured);
            if (type == null) {
                getLogger().warning("Ignoring unsupported crop in config.yml: " + configured);
            } else {
                enabledCrops.add(type);
            }
        }
        survivalCost = getConfig().getBoolean("survival-replant-cost", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.WATER) {
            return;
        }

        Block target = event.getToBlock();
        CropType crop = CropType.forCrop(target.getType());
        if (crop == null || !enabledCrops.contains(crop)) {
            return;
        }

        if (!(target.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        PendingKey key = PendingKey.of(target);
        if (!pending.containsKey(key)) {
            pending.put(key, new PendingCrop(crop, !survivalCost));
            pendingDirty = true;
        }
    }

    /** Pay the survival replant cost from the actual vanilla harvest drops. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakByLiquid(BlockBreakBlockEvent event) {
        PendingKey key = PendingKey.of(event.getBlock());
        PendingCrop pendingCrop = pending.get(key);
        if (pendingCrop == null || pendingCrop.seedPaid()) {
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
            boolean correctSoil = block.getRelative(BlockFace.DOWN).getType() == crop.type().soil();
            boolean eligible = ReplantRules.mayReplant(current.isAir(), current == Material.WATER,
                    crop.seedPaid(), correctSoil);
            if (eligible && protection.mayReplant(block)) {
                BlockData fresh = crop.type().crop().createBlockData();
                if (fresh instanceof Ageable ageable) {
                    ageable.setAge(0);
                    block.setBlockData(fresh);
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
}
