# Village Builder — Vision

## The Core

Villages are alive. They grow on their own. They benefit from you, but they don't need you.

Village Builder makes the player a neighbor — not a savior, not a god, not a manager. The village was here before you arrived. It'll keep growing after you leave. But if you stick around and help, it grows faster and maybe a little differently than it would have on its own.

## The Fantasy

You stumble on a plains village. There's a Builder villager working at a table near the bell. You open the table and see a plan for a small house — housing is tight, two villagers are sharing a bed. The resource pool is half-full; the Builder has been slowly accumulating cobblestone and logs on their own. You dump some spare stone and wood from your inventory. The progress bar jumps. Next morning at dawn, a house appears. Every villager in the village walks over to look at it before starting their day.

You trade with the Builder. They're buying stone cheap — the village already has plenty. But they're paying well for glass, because the plan calls for it. You check the alternative plans: a small house with one bed, or a large farmhouse with a bed and cropland. You buy the farmhouse plan instead. Now the material list shifts.

A week later the village has grown. New houses, a workshop, a farm. The Builder is eyeing defenses now — there are enough villagers to attract raids. You haven't been back in three days, but the resource pool has been ticking up on its own. The village didn't wait for you.

## Principles

> Principles marked with **[live]** are implemented. Those marked with **[planned]** describe where the mod is headed — see STRUCTURE_DISCOVERY.md for technical roadmaps.

### 1. The player is a patron, not an architect **[live]**
The player doesn't choose what to build from a catalog. The village knows what it needs. The player can influence the choice by purchasing one of a few alternative plans that all satisfy the same need. The options are few and all valid — not a design tool, a nudge. The player accelerates growth — they don't cause it. The trade screen tells you what the village wants without needing a separate UI.

### 2. Structures come from the world, not the mod **[live]**
The default building pool is the vanilla per-biome village structure set. Material requirements are derived from the actual NBT structure files — block composition analyzed, simplified to raw base materials, and rounded for clean stacks. A plains village builds plains structures. A taiga village builds taiga structures. No hardcoded lists per biome.

> *Vanilla structures are discovered by analyzing a known list of template paths per biome. Hardcoded fallbacks exist only for the Builder's Workshop (which contains the mod's custom block). Discovered structures take priority over fallbacks. See DEVELOPMENT.md "Known Limitations" for future improvements.*

### 3. The system is open to other mods **[live]**
Any mod can register structures into the building pool. A castle mod offers a small fortified house when the village needs housing. A mail mod delivers materials from across the map. The system doesn't care where the structure came from — it cares whether it meets the need. You walk into a village running three structure mods and it just looks like a village that grew.

> *Material donation API and structure registration API are both live via `VillageBuilderAPI`. Third-party structures compete on equal footing with vanilla structures in the candidate pool.*

### 4. Biomes shape personality **[live]**
Villages in different biomes lean toward different specializations. Every village handles survival basics first — housing, food — but once those are met, biome affinity guides what comes next through the structure pool. A plains village gets plains-style structures; a taiga village gets taiga-style structures. Not a hard lock. A weighted tendency shaped by available structure candidates.

> *The needs analyzer reads the village biome and queries the structure registry for biome-matching structures. Vanilla structures are tagged per biome at discovery time. All biomes share the same need priorities — differentiation comes from the structure pool, not the priority order.*

### 5. Friction is real but shared **[live]**
Material costs are grounded in reality — derived from actual structure composition. Bigger buildings genuinely cost more. But the friction is distributed: the Builder accumulates resources on their own, and in multiplayer, multiple players can contribute. The player's role is to tip the balance.

### 6. Ceremony is quiet **[live]**
A new building appears at dawn. A chat message tells players in the village what was built. Every villager paths to the new structure to look at it before starting their day. The warmth comes from watching the village change over time, not from momentary spectacle.

### 7. Standalone with roots **[live]**
This mod works completely on its own. But it exposes clean integration points so that other mods can deliver materials, gate access, register structures, or hook in without Village Builder knowing or caring about their internals.

## Village Lifecycle

**Discovery** — A village is found. A Builder's Table exists (spawned naturally or placed by a player). The Builder analyzes what the village needs most.

**Planning** — A construction plan is selected based on the highest-priority need. The Builder's Table shows the plan, required materials, and current stockpile. Alternative plans (1-3 options that also satisfy the need) are available as trades from the Builder.

**Accumulation** — The Builder gathers resources over time on their own. Each dawn, builders focus on one material type (round-robin through what the plan needs most), gathering at a rate proportional to the number of builders present. Players can accelerate this by trading materials or depositing directly into the Builder's Table. Trade prices slide based on what the village already has vs. what it still needs. *[Autonomous gathering is live. Village-aware purchase and supply pricing are both live (pays more for needed materials, charges more for scarce supplies). Anti-exploit tuning is planned.]*

**Construction** — When materials are sufficient and a Builder is present, the structure is placed at dawn. A simple announcement is sent to players in the village. All villagers path to view the new building before going about their day.

**Reassessment** — After construction, the Builder re-analyzes village needs and selects the next plan. The cycle continues.

## Need Priorities

Villages think about survival first, then quality of life, then character.

1. **Housing** — Enough beds for every villager. Always the top priority when unmet.
2. **Food** — Enough farmland and food supply to sustain the population.
3. **Profession** — Workstations and workshops so villagers can specialize.
4. **Defense** — Walls, iron golems, guard posts as population grows.
5. **Utility** — Storage, wells, community infrastructure.
6. **Prosperity** — Libraries, meeting points, churches, biome-flavored specializations. This is where personality emerges.

## Structure Sources **[live]**

**Vanilla pool (default):** Per-biome village structures from Minecraft's own structure pools. Analyzed at server startup. Material requirements derived from NBT block composition, simplified to base materials (all stone variants map to cobblestone, all wood variants map to their log type, etc.).

**Mod-registered structures:** Other mods register structures with metadata: what need they satisfy, what materials they require, and optionally what biomes they prefer. These enter the candidate pool alongside vanilla structures and compete on equal footing.

**Hardcoded fallbacks:** A small set of simple structures exists as safety nets in case structure pool analysis fails or returns nothing. These are not the intended path — they're the floor.

## Trade Economy **[live — anti-exploit tuning planned]**

The Builder buys and sells based on village state, not fixed prices.

- **Buying from player:** The Builder pays more for what the village needs and less for what it already has.
- **Selling to player:** The Builder sells alternative construction plans. Each plan satisfies the current village need but in a different way. Plans cost emeralds. Buying a plan overrides the current selection.
- **No exploitation:** Trade prices are tuned to discourage using the Builder as a money printer. The Builder is a sink for surplus materials and a source of village agency, not a trading exploit. *[Purchase and supply pricing are both village-aware. Full anti-exploit tuning is planned.]*

## What This Mod Is Not

**Not a city builder.** The player doesn't zone, plan layouts, or manage resources on a spreadsheet. Villages grow organically.

**Not a progression system.** There's no tech tree, no unlocks, no "you must build X before Y" beyond natural need priorities. *(Builder trade tiers do gate higher-cost plans and materials behind villager level-ups, providing a soft sense of progression — but the village itself grows without gating.)*

## The Destination

You've been playing this world for months. You walk into the first village you ever helped — the plains one near spawn. It has twelve buildings now. A library. A workshop. Farmland stretching out past the original fence line. A Builder working on plans for a meeting hall. You remember when it was five houses and a well.

You pass through a taiga village you've never visited. It has seven buildings. Smaller than your village, but growing. The Builder there has been working alone.

The world feels alive in a way vanilla never quite achieves.

That's it. That's the mod.
