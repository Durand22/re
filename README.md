# WaterReplant

WaterReplant is a Paper 26.2 plugin for survival crop farms. Water harvests mature crops; once the water clears, the plugin replants the crop if the correct soil is still in place.

## Survival behavior

- Replanting costs one seed or crop item from the harvest drops. If the crop does not drop a seed, it is not replanted.
- Wheat, carrots, potatoes, beetroot, and nether wart are supported by default.
- Nether wart requires soul sand; the other supported crops require farmland.
- Pending crops are saved across server restarts.
- If WorldGuard is installed, crops are only replanted where the `BUILD` flag allows building.

## Install

1. Copy `build/libs/re-1.0.jar` into the Paper server's `plugins` folder.
2. Start the server. The plugin creates `plugins/WaterReplant/config.yml` on first launch.
3. Edit the config if you want to change supported crops or disable the seed cost, then restart the server.

## Configuration

`crops` accepts `WHEAT`, `CARROTS`, `POTATOES`, `BEETROOTS`, and `NETHER_WART`.

`survival-replant-cost` defaults to `true`. Set it to `false` for free replanting.

`worldguard-protection` defaults to `true`; it only has an effect when WorldGuard is installed.

`check-interval-ticks` controls how often the plugin checks whether the harvested spots are clear of water.
