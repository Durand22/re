package com.example.waterreplant;

final class ReplantRules {
    private ReplantRules() {}

    static boolean mayReplant(boolean spotIsAir, boolean waterPresent, boolean seedPaid, boolean correctSoil) {
        return spotIsAir && !waterPresent && seedPaid && correctSoil;
    }
}
