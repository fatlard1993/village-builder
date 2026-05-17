# Village Builder API Integration Guide

## For Mod Developers

The Village Builder mod provides an API for other mods to interact with village construction.

### Available API Methods

```java
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.api.VillageBuilderAPI.DonationResult;

// Process donated items — returns accepted, rejected, and overflow info
DonationResult result = VillageBuilderAPI.processDonatedMaterials(
    world, donationPos, donatedItems
);
// result.accepted()    — items the village kept as building materials
// result.rejected()    — items that aren't building materials (return to sender, etc.)
// result.overflowLost() — number of building material items that didn't fit (lost — not dropped as entities)

// Check if an item is a valid building material
boolean valid = VillageBuilderAPI.isBuildingMaterial(Items.COBBLESTONE); // true
boolean invalid = VillageBuilderAPI.isBuildingMaterial(Items.DIAMOND_SWORD); // false

// Check if an item is currently needed for the village's active construction plan
boolean needed = VillageBuilderAPI.isNeededForConstruction(world, villagePos, Items.OAK_LOG);

// Get current construction status text (or null if no active plan)
Text status = VillageBuilderAPI.getConstructionStatus(world, villagePos);
```

### Village-Mail Integration Example

```java
public class VillageMailIntegration {
    public static void deliverMaterials(ServerWorld world, BlockPos mailboxPos, List<ItemStack> items) {
        if (!FabricLoader.getInstance().isModLoaded("village-builder")) {
            return;
        }

        DonationResult result = VillageBuilderAPI.processDonatedMaterials(
            world, mailboxPos, items
        );

        // result.accepted() = items the village kept as building materials
        // result.rejected() = items that aren't building materials (return to sender, etc.)
        // result.overflowLost() = number of items lost because the village inventory was full

        if (result.overflowLost() > 0) {
            // Handle overflow — the village's 27-slot inventory was full
        }
    }
}
```

### Structure Registration

Other mods can register structures into the village building pool.

**Important:** The structure registry is rebuilt on every world load. Use `registerStructurePersistent` (recommended) to ensure your structure survives reloads. Call it from your mod's `onInitialize`:

```java
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.building.StructureType.MaterialRequirement;
import justfatlard.village_builder.village.VillageNeedsAnalyzer.VillageNeed;

// In your ModInitializer.onInitialize():
VillageBuilderAPI.registerStructurePersistent(
    Identifier.of("mymod", "fortified_house"),
    "Fortified House",
    VillageNeed.HOUSING,
    List.of(
        new MaterialRequirement(Items.COBBLESTONE, 200),
        new MaterialRequirement(Items.IRON_INGOT, 16),
        new MaterialRequirement(Items.OAK_LOG, 80)
    ),
    Set.of("plains", "taiga"),  // empty set = all biomes
    7  // clearance size in blocks
);
```

Alternatively, use `registerStructure` for one-time registration (e.g., after world load events), but note that the registration will be lost on the next world reload unless you re-register.

### NBT Template Registration

If your mod ships `.nbt` structure files, use `registerTemplatePersistent` to register them with a display name. The template ID must match a file at `data/{namespace}/structures/{path}.nbt` in your mod's jar. Village Builder will place the template directly when construction triggers.

```java
// Register an NBT template structure
// File: data/mymod/structures/castle_house.nbt
VillageBuilderAPI.registerTemplatePersistent(
    Identifier.of("mymod", "castle_house"),
    "Castle House",
    VillageNeed.HOUSING,
    List.of(
        new MaterialRequirement(Items.STONE, 200),
        new MaterialRequirement(Items.OAK_LOG, 80),
        new MaterialRequirement(Items.IRON_INGOT, 16)
    ),
    Set.of("plains", "taiga"),  // empty set = all biomes
    9  // clearance size in blocks
);
```

**Multi-need structures:** A structure can satisfy multiple needs. Use `Set.of(VillageNeed.HOUSING, VillageNeed.FOOD)` for a farmhouse with beds that satisfies both housing and food needs. The single-need convenience methods wrap the value in a `Set.of()` for you.

The display name you provide is what players see in the Builder's Table GUI and trade offers. Choose something a villager would say — "Castle House", not "mymod_castle_house_v2".

Registered structures compete on equal footing with vanilla structures. The village will choose them based on need and biome match.

### Event Hooks

React to village lifecycle events without mixins:

```java
// Called when a structure is built
VillageBuilderAPI.onConstructionComplete((world, villageCenter, structureName, buildPos) -> {
    // Send a celebration message, spawn particles, update your mod's state, etc.
    LOGGER.info("Village at {} built: {}", villageCenter, structureName);
});

// Called when a village's construction plan changes
VillageBuilderAPI.onPlanChanged((world, villageCenter, newPlanName) -> {
    // Update displays, notify players, adjust pricing, etc.
    LOGGER.info("Village at {} now planning: {}", villageCenter, newPlanName);
});
```

Register these in your `onInitialize`. **Important:** Listeners are cleared on world unload to prevent stale references. If your mod needs listeners to survive across world reloads, re-register them on each world load event (e.g., via `ServerWorldEvents.LOAD`).

### Biome Constants

Use these constants instead of magic strings for biome preferences:

```java
VillageBuilderAPI.BIOME_PLAINS   // "plains"
VillageBuilderAPI.BIOME_TAIGA    // "taiga"
VillageBuilderAPI.BIOME_DESERT   // "desert"
VillageBuilderAPI.BIOME_SAVANNA  // "savanna"
VillageBuilderAPI.BIOME_SNOWY    // "snowy"

// Example: register a structure for plains and taiga
VillageBuilderAPI.registerStructurePersistent(
    Identifier.of("mymod", "lodge"),
    "Hunting Lodge",
    VillageNeed.PROFESSION,
    requirements,
    Set.of(VillageBuilderAPI.BIOME_PLAINS, VillageBuilderAPI.BIOME_TAIGA),
    8
);
```

### Building Materials

The full list of accepted building materials is drawn from two sources: `BuilderTrades.MATERIAL_POOL_SET` and `MaterialMapping.getAllMaterialItems()`. This includes:
- Stone types: Cobblestone, Stone, Stone Bricks, Sand, Sandstone, Bricks, Terracotta
- Wood types: All log and plank variants (Oak, Spruce, Birch, Dark Oak, Jungle, Acacia, Mangrove, Cherry, Bamboo, Crimson, Warped)
- Glass, Dirt, Grass Block, Clay Ball
- Metals: Iron Ingot, Gold Ingot
- Wool variants, Coal, Charcoal
- Tools: Iron/Diamond Pickaxe, Iron/Diamond Axe, Iron Shovel, Wooden Hoe
- Gems: Diamond
- Special: Anvil, Bell, Book

**Note:** If `processDonatedMaterials` is called and no Builder's Table exists nearby, all items are returned in `result.rejected()`.

**Note:** `processDonatedMaterials` sends a notification message to players within 64 blocks when materials are accepted. If your mod also sends notifications, consider suppressing your own to avoid double messages.

**Note:** The village inventory has 27 slots. During trade interactions, overflow materials are dropped as item entities near the table. Via the API (`processDonatedMaterials`), overflow items are **not** dropped — they are silently lost. Check `result.overflowLost()` to detect this and handle accordingly.

**Note:** All API methods access shared mutable state and should only be called from the server thread.

### Optional Dependency

Add to your `fabric.mod.json`:

```json
{
  "suggests": {
    "village-builder": "*"
  }
}
```

This makes village-builder an optional integration — your mod works without it, but adds features when both are installed.
