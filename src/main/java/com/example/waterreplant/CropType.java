package com.example.waterreplant;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

enum CropType {
    WHEAT("WHEAT", "WHEAT_SEEDS", "FARMLAND"),
    CARROTS("CARROTS", "CARROT", "FARMLAND"),
    POTATOES("POTATOES", "POTATO", "FARMLAND"),
    BEETROOTS("BEETROOTS", "BEETROOT_SEEDS", "FARMLAND"),
    NETHER_WART("NETHER_WART", "NETHER_WART", "SOUL_SAND");

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
