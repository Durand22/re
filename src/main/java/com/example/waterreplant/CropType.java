package com.example.waterreplant;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

enum CropType {
    WHEAT("WHEAT", "WHEAT_SEEDS", "FARMLAND"),
    CARROTS("CARROTS", "CARROT", "FARMLAND"),
    POTATOES("POTATOES", "POTATO", "FARMLAND"),
    BEETROOTS("BEETROOTS", "BEETROOT_SEEDS", "FARMLAND"),
    NETHER_WART("NETHER_WART", "NETHER_WART", "SOUL_SAND"),
    PUMPKIN("PUMPKIN", "PUMPKIN_SEEDS", "FARMLAND"),
    MELON("MELON", "MELON_SEEDS", "FARMLAND"),
    SWEET_BERRY_BUSH("SWEET_BERRY_BUSH", "SWEET_BERRIES", "BERRY_SOIL"),
    SUGAR_CANE("SUGAR_CANE", "SUGAR_CANE", "SAND"),
    CACTUS("CACTUS", "CACTUS", "SAND"),
    BAMBOO("BAMBOO", "BAMBOO", "DIRT"),
    KELP("KELP", "KELP", "WATER"),
    TORCHFLOWER_CROP("TORCHFLOWER_CROP", "TORCHFLOWER_SEEDS", "FARMLAND"),
    PITCHER_CROP("PITCHER_CROP", "PITCHER_POD", "FARMLAND");

    private final String cropMaterial;
    private final String seedMaterial;
    private final String soilMaterial;

    CropType(String cropMaterial, String seedMaterial, String soilMaterial) {
        this.cropMaterial = cropMaterial;
        this.seedMaterial = seedMaterial;
        this.soilMaterial = soilMaterial;
    }

    String seedMaterialName() {
        return seedMaterial;
    }

    String soilMaterialName() {
        return soilMaterial;
    }

    Material crop() {
        return Material.valueOf(cropMaterial);
    }

    Material soil() {
        return Material.valueOf(soilMaterial);
    }

    static CropType forCrop(Material material) {
        return fromConfigName(material.name());
    }

    static boolean isMature(int age, int maximumAge) {
        return age >= maximumAge;
    }

    boolean needsAgeToHarvest() {
        return switch (this) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, SWEET_BERRY_BUSH,
                    TORCHFLOWER_CROP, PITCHER_CROP -> true;
            default -> false;
        };
    }

    boolean replantsAfterHarvest() {
        return switch (this) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, SWEET_BERRY_BUSH,
                    TORCHFLOWER_CROP, PITCHER_CROP -> true;
            default -> false;
        };
    }

    boolean regrowsFromBase() {
        return switch (this) {
            case SUGAR_CANE, CACTUS, BAMBOO, KELP -> true;
            default -> false;
        };
    }

    boolean addedWithExpandedFarmSupport() {
        return switch (this) {
            case PUMPKIN, MELON, SWEET_BERRY_BUSH, SUGAR_CANE, CACTUS, BAMBOO, KELP,
                    TORCHFLOWER_CROP, PITCHER_CROP -> true;
            default -> false;
        };
    }

    boolean hasCorrectSoil(Material material) {
        if ("BERRY_SOIL".equals(soilMaterial)) {
            return switch (material) {
                case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, MOSS_BLOCK, MYCELIUM -> true;
                default -> false;
            };
        }
        return "WATER".equals(soilMaterial) ? material == Material.WATER
                : material == soil();
    }

    static CropType fromConfigName(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    int seedDropIndex(List<CropDrop> drops) {
        for (int index = 0; index < drops.size(); index++) {
            CropDrop drop = drops.get(index);
            if (drop.amount() > 0 && seedMaterial.equals(drop.material())) {
                return index;
            }
        }
        return -1;
    }
}
