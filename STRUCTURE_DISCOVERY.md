# Structure Discovery Reference

> This documents how the structure discovery system works. Open questions and known limitations have been moved to the "Known Limitations" section of DEVELOPMENT.md.

## Architecture

Structure definitions come from a runtime `StructureRegistry`, populated at server startup from three sources:

1. **Discovered structures** — `StructureAnalyzer` scans vanilla village NBT templates, derives material requirements via `MaterialMapping`, classifies structures by need (beds → housing, crops → food, workstations → profession), and tags them with biome preferences.

2. **Mod-registered structures** — Other mods can register structures via `VillageBuilderAPI` with metadata: need category, materials, biome preferences. These compete on equal footing with vanilla structures.

3. **Fallback structures** — The `StructureType` enum provides hardcoded definitions as a safety net. Fallbacks are used only when no discovered or mod-registered structure matches a need+biome query.

## Key Files

| File | Role |
|------|------|
| `MaterialMapping.java` | Block → base material simplification (stone variants → cobblestone, etc.) |
| `StructureEntry.java` | Runtime structure definition record |
| `StructureRegistry.java` | Queryable registry (by need + biome) |
| `StructureAnalyzer.java` | NBT template analysis + vanilla pool scanning |
| `StructureType.java` | Hardcoded fallback definitions (Builder's Workshop only) |
| `StructurePlan.java` | Plan instance, backed by either StructureEntry or StructureType |
| `village-builder.accesswidener` | Widens `StructureTemplate.blockInfoLists` for block analysis |

## How Discovery Works

1. On world load, `Main` clears and repopulates the registry
2. `registerBuildersWorkshops()` adds per-biome Builder's Workshop entries
3. `StructureAnalyzer.discoverModStructures()` iterates known vanilla village pool paths per biome
4. For each template path, the analyzer loads the NBT, counts blocks via `MaterialMapping`, rounds to clean stacks, classifies need, and registers the entry
5. Discovered entries override fallbacks with matching IDs
6. `runReloadCallbacks()` re-registers any mod-registered structures

## Biome Awareness

The `VillageNeedsAnalyzer` reads the biome at the village center and queries the registry with a biome key. Biome keys are simplified categories: `plains`, `taiga`, `desert`, `savanna`, `snowy`. Structures discovered from vanilla pools are tagged with their source biome. Fallback structures have no biome preference and match everywhere.
