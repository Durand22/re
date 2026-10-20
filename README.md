# WaterReplant 2.0

WaterReplant is a Paper 26.2 survival farming plugin. Water can harvest configured mature crops; immature age-based crops are protected from flowing water. Replantable crops return after the water clears when the correct soil remains and the seed/item cost is paid.

## Water-harvest support

Enabled by default: wheat, carrots, potatoes, beetroot, nether wart, pumpkins, melons, sweet berry bushes, sugar cane, cactus, bamboo, kelp, torchflower, and pitcher plants. Per-crop harvesting and survival costs are configurable.

- Wheat, carrots, potatoes, beetroot, nether wart, and sweet berry bushes can be replanted when a matching seed/item is present in the vanilla drops.
- Pumpkins and melons are harvested as fruit; their stems regrow fruit naturally, so the plugin does not place another fruit block.
- Sugar cane, cactus, bamboo, and kelp protect their base and let the remaining plant regrow naturally.
- Optional harvest/replant sounds, WorldGuard BUILD checks, pending-crop persistence, and a configurable pending-work cap are included. The cap defaults to `0` (unlimited).

## Redstone dispenser tools

All enabled by default in `dispenser-tools`:

- **Shears:** use vanilla dispenser shearing on nearby sheep, with WorldGuard protection checks.
- **Pickaxes:** break one suitable block directly in front, using tool-based drops.
- **Axes:** fell connected logs, limited to 64 logs and available axe durability per pulse. If `tree-replant` is enabled and a matching sapling/propagule is in the dispenser, one is planted at the tree base.
- **Swords:** hit the nearest hostile mob in front for 6 damage. Players, villagers, pets, and passive mobs are not targets.

Successful mining, felling, and attacks consume tool durability. Drops spawn normally where hoppers can collect them. Tree replanting costs one sapling supplied in the dispenser.

## Install and test

1. Copy `build/libs/re-2.0.jar` into the Paper server's `plugins` folder.
2. Start the server and confirm WaterReplant appears in `/plugins`.
3. In a test world, pulse dispensers facing a log, mineable block, hostile mob, or sheep. Put the required tool (and a sapling if you want tree replanting) in the dispenser.
4. Test water harvesting on a mature crop row and check that immature crops stay intact.

## Configuration

Each entry under `crops` has `enabled` and `survival-cost` options. Dispenser features can be toggled individually. `max-pending-crops: 0` means unlimited; set a positive value to cap pending crop spots. `worldguard-protection` defaults to true and applies when WorldGuard is installed.

Older configurations using a list of crop names and `survival-replant-cost` remain supported.

