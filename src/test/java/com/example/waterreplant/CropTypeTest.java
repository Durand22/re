package com.example.waterreplant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CropTypeTest {

    @Test
    void supportedCropsHaveSurvivalAppropriateSeedAndSoil() {
        assertEquals("WHEAT_SEEDS", CropType.WHEAT.seedMaterialName());
        assertEquals("CARROT", CropType.CARROTS.seedMaterialName());
        assertEquals("POTATO", CropType.POTATOES.seedMaterialName());
        assertEquals("BEETROOT_SEEDS", CropType.BEETROOTS.seedMaterialName());
        assertEquals("NETHER_WART", CropType.NETHER_WART.seedMaterialName());
        assertEquals("FARMLAND", CropType.WHEAT.soilMaterialName());
        assertEquals("SOUL_SAND", CropType.NETHER_WART.soilMaterialName());
    }

    @Test
    void seedCostFindsRequiredDropWithoutSelectingTheHarvest() {
        List<CropDrop> drops = List.of(
                new CropDrop("WHEAT", 3),
                new CropDrop("WHEAT_SEEDS", 2)
        );

        assertEquals(1, CropType.WHEAT.seedDropIndex(drops));
    }

    @Test
    void seedCostDoesNotFindSeedWhenHarvestHasNone() {
        List<CropDrop> drops = List.of(new CropDrop("WHEAT", 3));

        assertEquals(-1, CropType.WHEAT.seedDropIndex(drops));
    }

    @Test
    void seedCostFindsSingleSeedStack() {
        List<CropDrop> drops = List.of(new CropDrop("BEETROOT_SEEDS", 1));

        assertEquals(0, CropType.BEETROOTS.seedDropIndex(drops));
    }

    @Test
    void configNamesAreCaseInsensitiveAndRejectUnsupportedCrops() {
        assertEquals(CropType.NETHER_WART, CropType.fromConfigName("nether_wart"));
        assertEquals(CropType.PUMPKIN, CropType.fromConfigName("PUMPKIN"));
        assertEquals(CropType.SWEET_BERRY_BUSH, CropType.fromConfigName("sweet_berry_bush"));
        assertEquals(CropType.TORCHFLOWER_CROP, CropType.fromConfigName("torchflower_crop"));
    }

    @Test
    void fruitAndRegrowingPlantsDoNotGetReplantedAsAgeZeroCrops() {
        assertEquals(false, CropType.PUMPKIN.replantsAfterHarvest());
        assertEquals(false, CropType.SUGAR_CANE.replantsAfterHarvest());
        assertEquals(true, CropType.SUGAR_CANE.regrowsFromBase());
        assertEquals(true, CropType.SWEET_BERRY_BUSH.replantsAfterHarvest());
    }
}
