# Village Builder — Review Briefing

## Repo Snapshot
- **Mod**: Village Builder, Fabric 1.16 + MC 26.1.2, Java 25
- **Source files**: 28 Java files, ~2600 LOC Java
- **Tests**: Zero
- **Build**: Gradle / Fabric Loom

---

## Architecture Map

| File | Role |
|---|---|
| `Main.java` | Mod init: registers all game objects, wires all events, command tree |
| `VillageDataManager.java` | ~920 LOC god object: village lifecycle, tick loop, material gathering, construction, orphan cleanup, villager-path nudging |
| `VillageData.java` | Per-village state: 27-slot inventory, current plan, built-structure list, NBT serialization. Contains inner `VillageInventory` class |
| `VillageNeedsAnalyzer.java` | Scans village area, classifies needs, recommends plans. Caches metrics 1200 ticks |
| `BuildingManager.java` | Places structures from NBT templates or hardcoded blueprints; does terrain prep and path connection |
| `StructureRegistry.java` | Queryable map of registered structures; seasonal gating; reload callbacks |
| `StructureAnalyzer.java` | Discovers ~140 vanilla NBT templates at startup, classifies needs from block composition |
| `VillageBuilderAPI.java` | Public API: donate materials, register structures, listen to events |
| `VillagerEntityMixin.java` | Hooks `rewardTradeXp` to route materials/plans from trade completions |

**Data flow**: `StructureAnalyzer` → `StructureRegistry` → `VillageNeedsAnalyzer` → `VillageData` (plan) → `VillageDataManager` (tick/build) → `BuildingManager`

---

## Deterministic Findings

### F1 — Duplicate resource tree (structural confusion, not runtime error)
All JSON/PNG resources exist in **two** locations:
- `src/main/resources/assets/village-builder/...` ← correct, gets packaged
- `src/main/java/village-builder/...` and `src/main/java/minecraft/...` ← orphaned copies never packaged

~20 files affected. Gradle does not package files from `src/main/java/` unless they match a non-Java pattern in `processResources`. These are dead weight that will mislead future contributors.

### F2 — Two-map identity architecture (VillageDataManager)
VillageDataManager holds two maps that start as copies and are manually kept in sync:
- `villageDataCache` — runtime: populated on access, evicted after 12000 ticks idle
- `persistentVillages` — backing: all known villages, survives cache eviction

**Invariant is not enforced**: `resolveVillageData()` has three proximity tiers:
1. Exact match → return cached
2. Within 16 blocks → migrate (moves data, removes old key from both maps)
3. Within 64 blocks → share (adds the same VillageData object under a new key in `villageDataCache` only — NOT in `persistentVillages`)

The 64-block share path silently aliases two cache keys to one data object. When orphan cleanup runs, it may see the alias key as orphaned and remove the data from `persistentVillages`, while the canonical key in the cache still points to it. Result: the village survives in memory but won't load on next restart.

### F3 — Synchronized on NonNullList (fragile)
`VillageData` uses `synchronized(this.inventory)` throughout. The `inventory` field is a `NonNullList<ItemStack>`. Java's intrinsic locks on collections are not a documented contract of NonNullList — the list implementation could be replaced without the lock behavior changing. More concretely: `addToInventory()` is private and never called without the lock, but nothing enforces this — a future refactor could call it directly. The double-lock pattern in `VillageDataManager.gatherMaterials()` (outer lock on `inventoryStacks`, inner on `this.inventory`) works because Java reentrant locks allow re-entry from the same thread, but the intent is opaque.

### F4 — DEVELOPMENT.md compatibility section is stale
```
Compatibility:
- Minecraft: 1.21.11
- Fabric Loader: 0.18.1+
- Fabric API: 0.140.2+1.21.11
- Java: 21+
- Yarn mappings: 1.21.11+build.3
```
All wrong after the 26.1.2 update. The rest of DEVELOPMENT.md is accurate; only this section lags.

### F5 — Patron data not persisted
`VillageData.planPatronUuid` and `planPatronName` are `transient` — excluded from NBT. If the server restarts after materials are gathered but before construction completes, the patron credit is lost silently. Build announcements after restart won't name the patron.

### F6 — VillageNeedsAnalyzer bed-count loop is unsampled
The food/farmland scan uses `sampleStep=2` (samples 25% of blocks). The bed-count scan does not — it iterates every x,z at every y from -10 to +20. For a 32-block radius, that's ~3,217 columns × 31 y-levels ≈ 99,800 block reads per analysis per village. At 20+ villages on a server this runs every 60 seconds and could cause measurable lag.

### F7 — `getVanillaVillagePools()` hardcoded
~140 template paths are manually listed. If MC 26.1.2 renamed, removed, or added village templates, some paths are silently stale. Discovery failure logs ERROR and falls back to Builder's Workshop only. Documented known limitation, but the update to 26.1.2 may have shifted paths.

### F8 — `StructurePlan` dual-backing creates repeated branching
`StructurePlan` wraps either a `StructureEntry` or a `StructureType`. This forces every consumer to do both a registry lookup AND a `StructureType.fromId()` lookup (e.g., `buildAndGatherVillagers()` lines 550-566). The branching re-appears in `VillageData.fromNbt()` and `VillagerEntityMixin.getPlanFromPaper()`. The `StructureType` path exists only as a fallback for the single `BUILDERS_WORKSHOP` enum value, but the pattern propagates across 5+ sites.

### F9 — Static `getVillageBiome()` in VillageDataManager
A pure utility function (world + pos → biome string) lives as a package-private static on VillageDataManager. It has no dependency on manager instance state. `VillageNeedsAnalyzer` calls it via the class name, creating a coupling to the manager class that isn't necessary. Should live in a shared utility or on the analyzer itself.

### F10 — `GATHER_RATES` missing tool/bell/anvil items
DEVELOPMENT.md documents: "Tools/Bells/Anvils: 1 per builder per dawn". The `GATHER_RATES` map contains `Items.DIAMOND` at rate 1 but no tool, bell, or anvil items. If the current plan requires those, `getGatherRate()` returns the default of 1, which matches the documented rate — so behavior is correct, but the documentation implies explicit entries that aren't there.

### F11 — `findBuildingSpot()` greedy early return
Line 741: `if (distToCenter < 400.0) return testPos;` — returns immediately on any candidate within 20 blocks of center, bypassing the rest of the 40 attempts that might find a clearer spot. This is a performance trade-off, but it means placement quality in dense villages degrades unpredictably.

### F12 — `validateBuiltStructures()` uses weak heuristic
Checks: ground solid AND (not-air-at-center OR not-air-above). This will keep a record if literally one non-air block exists in the column. A player building a single torch where a structure used to be will prevent that record from being pruned.

### F13 — `VillageData.fromNbt()` swallows all deserialization errors
```java
} catch (Exception e) {
    LOGGER.error("Failed to deserialize village data — returning fresh village: {}", e.getMessage());
    return new VillageData(center);
}
```
Any corruption silently returns an empty village. Inventory, plan, and built-structure history are silently discarded. Players lose accumulated materials with no indication.

### F14 — Resource files misplaced in `src/main/java/` (see F1 above)
The structure NBT files, advancement JSONs, lang files, textures, and tag overrides are all duplicated. The java-path copies are the ones with which NBT generation scripts write output. The scripts (`create_workshops.py` etc.) are writing to `src/main/java/village-builder/structure/` but the packaged copies are in `src/main/resources/data/village-builder/structure/`. The scripts need to be pointed at the resources path.

### F15 — Well detection heuristic (VillageNeedsAnalyzer line 268-276)
"Has a well" is detected by: water block adjacent to cobblestone or stone bricks. A flooded basement with a cobblestone floor will satisfy this check. A Terralith-modded village with a stone pool won't necessarily match. The heuristic is weak but the stakes are low (just delays UTILITY build recommendation).

---

## Summary Counts
- Critical (would fail or lose data in production): F2 (village data loss on restart after 64-block share), F13 (silent material loss on NBT corruption)
- Warnings (will break under change pressure): F1, F3, F5, F8, F9
- Performance: F6
- Stale documentation: F4, F10
- Weak heuristics: F11, F12, F15
- Known limitation: F7
