# Village Builder

A Fabric mod for Minecraft 1.21.11 that makes villages grow on their own. A new Builder villager profession analyzes what the village needs, gathers materials at dawn, and constructs real structures from vanilla templates. You're a patron — you accelerate growth, but you don't cause it.

## How It Works

Villages analyze their own needs — housing, food, workshops, defenses — and select construction plans accordingly. Structures are **discovered at runtime** from Minecraft's own per-biome village templates, with material requirements derived from actual block composition. A plains village builds plains structures. A taiga village builds taiga structures. No hardcoded lists.

The Builder villager gathers materials autonomously each dawn. You can speed things up by trading materials or depositing them in the Builder's Table. When everything's ready, the structure appears at dawn. Villagers gather to look. The cycle continues.

## Features

### Builder Villager Profession
- New "Builder" villager profession with 5 levels of randomized trades
- Uses the Builder's Table as their workstation
- Buys building materials — pays more for what the village needs, less for surplus
- Sells structure plans that let you nudge the village's direction
- Gathers materials autonomously each dawn (rate scales with number of builders)

### Builder's Table
- Custom workstation block that serves as the village construction hub
- Opens a shared village inventory where players can deposit building materials
- Shows the current construction plan, progress percentage, and material requirements
- Crafted from a Crafting Table + Smithing Table + Stonecutter + Furnace (2x2)
- Can also be purchased from a Builder villager at level 1 for 4 emeralds
- Spawns naturally in new villages and may appear in existing ones near bells

### Dynamic Structure Discovery
- Scans vanilla village NBT templates at server startup across 5 biomes (plains, taiga, desert, savanna, snowy)
- Derives material requirements from actual block composition — bigger buildings genuinely cost more
- Classifies structures by need: beds = housing, crops = food, workstations = profession, walls = defense
- Other mods can register structures into the pool via the API — they compete on equal footing

### Village Needs Analysis
- Scans village conditions: housing, food supply, farmland, profession diversity, defenses
- Six-tier priority system: Housing > Food > Profession > Defense > Utility > Prosperity
- Automatically selects construction plans based on what the village lacks most

### Construction System
- Plans auto-assigned on village discovery and after each completed build
- Players can buy alternative plans from Builder villagers to influence the direction
- Materials tracked in a shared 27-slot inventory — what you see is what the village has
- Construction triggers at dawn when all materials are gathered and a Builder is present
- Villagers gather to admire new buildings; nearby players receive chat announcements

### Seasonal Surprises

Villages celebrate the seasons with special structures that appear in the build pool at certain times of year. Keep an eye on your villages during the holidays.

### Server Commands
- `/villagebuilder status` — View the nearest village's plan, progress, and materials
- `/villagebuilder list` — Count of all tracked villages
- `/villagebuilder reassign` — Force plan reassignment for the nearest village

### Optional Mod Integration
- **village-mail**: Registers post office and mailbox structures. Sends construction updates and milestone letters to players with mailboxes nearby.
- **village-quests**: Taking village items costs reputation. Build announcements include village names. Three custom quest types: fetch materials, survey build site, rush supplies.

### Limitations
- Overworld only. Builder's Tables placed in other dimensions will not function.
- Requires installation on both client and server (custom screen handler).

## Installation

1. Minecraft 1.21.11
2. Fabric Loader 0.18.1+
3. Fabric API 0.140.2+
4. Place the mod JAR in your mods folder

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## API

Other mods can integrate with Village Builder via `VillageBuilderAPI`. See [INTEGRATION_EXAMPLE.md](INTEGRATION_EXAMPLE.md) for full details.

```java
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.api.VillageBuilderAPI.DonationResult;

// Process donated items — accepts building materials, rejects non-materials
DonationResult result = VillageBuilderAPI.processDonatedMaterials(
    world, donationPos, donatedItems
);

// Register a custom structure for villages to build
VillageBuilderAPI.registerStructurePersistent(
    Identifier.of("mymod", "fortified_house"), "Fortified House",
    VillageNeedsAnalyzer.VillageNeed.HOUSING,
    List.of(new StructureType.MaterialRequirement(Items.COBBLESTONE, 200)),
    Set.of("plains", "taiga"), 7
);
```

## License

MIT License — See LICENSE file for details.

## Credits

Created by justfatlard
