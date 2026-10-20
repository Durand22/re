package com.example.waterreplant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplantRulesTest {
    @Test
    void distinguishesImmatureAndMatureCropAges() {
        assertFalse(CropType.isMature(6, 7));
        assertTrue(CropType.isMature(7, 7));
    }

    @Test
    void replantsOnlyWhenTheSpotIsClearPaidAndHasCorrectSoil() {
        assertTrue(ReplantRules.mayReplant(true, false, true, true));
    }

    @Test
    void doesNotReplantWhileWaterStillFillsTheSpot() {
        assertFalse(ReplantRules.mayReplant(false, true, true, true));
    }

    @Test
    void doesNotReplantWhenCropHasNoSeedToPayWith() {
        assertFalse(ReplantRules.mayReplant(true, false, false, true));
    }

    @Test
    void doesNotReplantWhenFarmlandOrRequiredSoilWasRemoved() {
        assertFalse(ReplantRules.mayReplant(true, false, true, false));
    }

    @Test
    void doesNotOverwriteAnotherBlockPlacedInTheCropSpot() {
        assertFalse(ReplantRules.mayReplant(false, false, true, true));
    }
}
