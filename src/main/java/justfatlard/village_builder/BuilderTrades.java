package justfatlard.village_builder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.util.BuildersTableFinder;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuilderTrades {

    private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");

    // ---------------------------------------------------------------------------
    // Material pool — keep exactly as-is
    // ---------------------------------------------------------------------------

    private static final Item[] MATERIAL_POOL = new Item[]{
        Items.COBBLESTONE, Items.STONE, Items.STONE_BRICKS,
        Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG,
        Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
        Items.BAMBOO_BLOCK, Items.CRIMSON_STEM, Items.WARPED_STEM,
        Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
        Items.DARK_OAK_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
        Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
        Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
        Items.GLASS, Items.GLASS_PANE, Items.DIRT, Items.GRASS_BLOCK,
        Items.SAND, Items.SANDSTONE, Items.BRICKS,
        Items.IRON_INGOT, Items.GOLD_INGOT,
        Items.WOOL.pick(DyeColor.WHITE), Items.WOOL.pick(DyeColor.RED), Items.WOOL.pick(DyeColor.BLUE),
        Items.TERRACOTTA, Items.DEEPSLATE_BRICKS,
        Items.TUFF_BRICKS, Items.COPPER_BLOCK.weathering().unaffected(), Items.ANVIL, Items.LEATHER,
        Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL
    };

    public static final Set<Item> MATERIAL_POOL_SET = new HashSet<>(Arrays.asList(MATERIAL_POOL));

    // ---------------------------------------------------------------------------
    // Per-level material tiers (items the builder will purchase from players)
    // ---------------------------------------------------------------------------

    /** Level 1 — stone/masonry */
    private static final Item[] LEVEL_1_ITEMS = {
        Items.COBBLESTONE, Items.STONE, Items.STONE_BRICKS
    };

    /** Level 2 — logs */
    private static final Item[] LEVEL_2_ITEMS = {
        Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG,
        Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
        Items.BAMBOO_BLOCK, Items.CRIMSON_STEM, Items.WARPED_STEM
    };

    /** Level 3 — planks */
    private static final Item[] LEVEL_3_ITEMS = {
        Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
        Items.DARK_OAK_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
        Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
        Items.CRIMSON_PLANKS, Items.WARPED_PLANKS
    };

    /** Level 4 — decorative/glass/wool/terracotta */
    private static final Item[] LEVEL_4_ITEMS = {
        Items.GLASS, Items.GLASS_PANE, Items.WOOL.pick(DyeColor.WHITE), Items.WOOL.pick(DyeColor.RED),
        Items.WOOL.pick(DyeColor.BLUE), Items.TERRACOTTA, Items.SANDSTONE, Items.BRICKS,
        Items.DEEPSLATE_BRICKS, Items.TUFF_BRICKS, Items.COPPER_BLOCK.weathering().unaffected()
    };

    /** Level 5 — metals/tools/leather */
    private static final Item[] LEVEL_5_ITEMS = {
        Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_PICKAXE,
        Items.IRON_AXE, Items.IRON_SHOVEL, Items.ANVIL, Items.LEATHER
    };

    // ---------------------------------------------------------------------------
    // Base exchange rates: how many of each item equals 1 emerald
    // ---------------------------------------------------------------------------

    private static int baseRate(Item item) {
        if (item == Items.COBBLESTONE || item == Items.STONE || item == Items.STONE_BRICKS) return 16;
        if (item == Items.OAK_LOG || item == Items.SPRUCE_LOG || item == Items.BIRCH_LOG
                || item == Items.DARK_OAK_LOG || item == Items.JUNGLE_LOG
                || item == Items.ACACIA_LOG || item == Items.MANGROVE_LOG
                || item == Items.CHERRY_LOG || item == Items.BAMBOO_BLOCK
                || item == Items.CRIMSON_STEM || item == Items.WARPED_STEM) return 8;
        if (item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS || item == Items.BIRCH_PLANKS
                || item == Items.DARK_OAK_PLANKS || item == Items.JUNGLE_PLANKS
                || item == Items.ACACIA_PLANKS || item == Items.MANGROVE_PLANKS
                || item == Items.CHERRY_PLANKS || item == Items.BAMBOO_PLANKS
                || item == Items.CRIMSON_PLANKS || item == Items.WARPED_PLANKS) return 12;
        if (item == Items.GLASS || item == Items.GLASS_PANE) return 6;
        if (item == Items.WOOL.pick(DyeColor.WHITE) || item == Items.WOOL.pick(DyeColor.RED) || item == Items.WOOL.pick(DyeColor.BLUE)) return 6;
        if (item == Items.TERRACOTTA) return 8;
        if (item == Items.SANDSTONE || item == Items.BRICKS) return 10;
        if (item == Items.DEEPSLATE_BRICKS || item == Items.TUFF_BRICKS) return 8;
        if (item == Items.COPPER_BLOCK.weathering().unaffected()) return 4;
        if (item == Items.IRON_INGOT) return 4;
        if (item == Items.GOLD_INGOT) return 2;
        if (item == Items.IRON_PICKAXE || item == Items.IRON_AXE || item == Items.IRON_SHOVEL) return 1;
        if (item == Items.ANVIL) return 1;
        if (item == Items.LEATHER) return 6;
        return 8; // fallback
    }

    // ---------------------------------------------------------------------------
    // buildTradeTable — returns level→TradeSet ResourceKey map for VillagerProfession
    //
    // In MC 26.1.2 villager trades are fully data-driven: the profession holds
    // ResourceKey<TradeSet> references that the server resolves from the
    // data/village-builder/trade_set/builder/level_N.json files at runtime.
    //
    // Dynamic pricing (village-state-aware) is injected at runtime via
    // VillagerEntityMixin.updateTrades, which calls buildDynamicOffers().
    // ---------------------------------------------------------------------------

    public static Int2ObjectMap<ResourceKey<TradeSet>> buildTradeTable() {
        Int2ObjectMap<ResourceKey<TradeSet>> map = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            map.put(level, ResourceKey.create(
                net.minecraft.core.registries.Registries.TRADE_SET,
                Identifier.fromNamespaceAndPath(Main.MOD_ID, "builder/level_" + level)
            ));
        }
        return map;
    }

    // ---------------------------------------------------------------------------
    // register — called from Main.onInitialize after profession registration
    // ---------------------------------------------------------------------------

    public static void register() {
        LOGGER.info("[{}] Builder trades wired (data-driven TradeSet keys for levels 1–5; "
                + "dynamic pricing active via VillagerEntityMixin.updateTrades)",
                Main.MOD_ID);
    }

    // ---------------------------------------------------------------------------
    // refreshVillagerOffers — kept exactly as-is
    // ---------------------------------------------------------------------------

    public static void refreshVillagerOffers(ServerLevel world, BlockPos villageCenter) {
        AABB area = new AABB(villageCenter).inflate(96);
        for (Villager villager : world.getEntitiesOfClass(Villager.class, area,
                v -> v.getVillagerData().profession().is(Main.BUILDER_KEY))) {
            villager.getOffers().clear();
        }
    }

    // ---------------------------------------------------------------------------
    // buildDynamicOffers — called by VillagerEntityMixin.updateTrades
    //
    // Constructs MerchantOffer objects for all levels unlocked by this villager's
    // current experience level, applying village-state-aware pricing where data
    // is available. Returns an empty list if the villager is not on a ServerLevel
    // or no builder's table is found nearby.
    // ---------------------------------------------------------------------------

    public static List<MerchantOffer> buildDynamicOffers(ServerLevel world, BlockPos villagerPos, int villagerLevel) {
        List<MerchantOffer> offers = new ArrayList<>();

        BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(world, villagerPos);
        VillageData data = (tablePos != null)
                ? Main.VILLAGE_DATA_MANAGER.getExistingVillageData(world, tablePos)
                : null;
        StructurePlan currentPlan = (data != null) ? data.getCurrentPlan() : null;

        // Material purchase trades — include all tiers up to the villager's level
        if (villagerLevel >= 1) addMaterialOffers(offers, LEVEL_1_ITEMS, data, currentPlan);
        if (villagerLevel >= 2) addMaterialOffers(offers, LEVEL_2_ITEMS, data, currentPlan);
        if (villagerLevel >= 3) addMaterialOffers(offers, LEVEL_3_ITEMS, data, currentPlan);
        if (villagerLevel >= 4) addMaterialOffers(offers, LEVEL_4_ITEMS, data, currentPlan);
        if (villagerLevel >= 5) addMaterialOffers(offers, LEVEL_5_ITEMS, data, currentPlan);

        // Level 5: flag stake — lets the player designate the next build site
        if (villagerLevel >= 5) {
            ItemStack flagStake = new ItemStack(Main.BUILDERS_FLAG_ITEM, 1);
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), flagStake, 3, 5, 0.05f));
        }

        // Plan sale trades — one per level starting at level 2
        for (int offerLevel = 2; offerLevel <= villagerLevel; offerLevel++) {
            MerchantOffer planOffer = buildPlanOffer(world, tablePos, offerLevel);
            if (planOffer != null) {
                offers.add(planOffer);
            }
        }

        return offers;
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds MerchantOffer instances for each item in the given tier array,
     * applying dynamic pricing based on village material needs.
     */
    private static void addMaterialOffers(
            List<MerchantOffer> offers,
            Item[] tierItems,
            VillageData data,
            StructurePlan currentPlan) {

        for (Item item : tierItems) {
            int rate = dynamicRate(item, data, currentPlan);
            // rate = how many items the player gives for 1 emerald
            // clamp: ItemCost count must be 1–64
            rate = Math.max(1, Math.min(64, rate));

            ItemCost costA = new ItemCost(item, rate);
            ItemStack result = new ItemStack(Items.EMERALD, 1);

            // maxUses=16, xp=2, priceMult=0.05f
            offers.add(new MerchantOffer(costA, result, 16, 2, 0.05f));
        }
    }

    /**
     * Applies dynamic pricing multipliers based on village need:
     * - needed and <25% stocked → rate halved (player gives fewer items — trade is more valuable)
     * - needed but >75% stocked → rate doubled (trade is less valuable)
     * - otherwise → base rate
     */
    private static int dynamicRate(Item item, VillageData data, StructurePlan currentPlan) {
        int base = baseRate(item);

        if (data == null || currentPlan == null) {
            return base;
        }

        // Check if item is required by the current plan
        int required = 0;
        for (StructureType.MaterialRequirement req : currentPlan.getRequirements()) {
            if (req.item() == item) {
                required = req.amount();
                break;
            }
        }

        if (required <= 0) {
            return base; // not needed — base rate
        }

        int have = data.getMaterialCount(item);
        float fraction = (required > 0) ? (float) have / required : 1.0f;

        if (fraction < 0.25f) {
            // Urgently needed — halve the cost (player gives fewer items per emerald)
            return Math.max(1, base / 2);
        } else if (fraction > 0.75f) {
            // Almost fully stocked — double the cost (less valuable trade)
            return Math.min(64, base * 2);
        }

        return base;
    }

    /**
     * Builds a plan-paper sale offer for the given level.
     * Queries VillageNeedsAnalyzer for recommended plans and selects the plan
     * at index (level - 2) so each level offers a different alternative.
     * Returns null if no plan is available for this slot.
     */
    private static MerchantOffer buildPlanOffer(ServerLevel world, BlockPos tablePos, int level) {
        if (tablePos == null) {
            return null;
        }

        try {
            VillageNeedsAnalyzer analyzer = new VillageNeedsAnalyzer(world, tablePos);
            List<VillageNeedsAnalyzer.VillageNeed> priorities = analyzer.analyzeNeedsByPriority();
            if (priorities.isEmpty()) {
                return null;
            }

            // Use the top need to query plans; offset by level index for variety
            VillageNeedsAnalyzer.VillageNeed topNeed = priorities.get(0);
            List<StructurePlan> plans = analyzer.getRecommendedPlans(topNeed);
            if (plans.isEmpty()) {
                return null;
            }

            int planIndex = level - 2; // level 2 → index 0, level 3 → index 1, etc.
            if (planIndex >= plans.size()) {
                // Wrap around rather than returning null when there are fewer plans than levels
                planIndex = planIndex % plans.size();
            }

            StructurePlan plan = plans.get(planIndex);
            Identifier structureId = plan.getStructureId();

            // Determine emerald cost
            int emeraldCost;
            StructureType structureType = StructureType.fromId(structureId.getPath());
            if (structureType != null) {
                emeraldCost = structureType.getEmeraldCost();
            } else {
                emeraldCost = level;
            }
            emeraldCost = Math.max(1, Math.min(64, emeraldCost));

            // Build plan paper ItemStack
            ItemStack planPaper = new ItemStack(Items.PAPER, 1);

            CompoundTag tag = new CompoundTag();
            tag.putString("village_builder_structure_id", structureId.toString());
            CustomData.set(DataComponents.CUSTOM_DATA, planPaper, tag);
            planPaper.set(DataComponents.ITEM_NAME,
                    Component.literal(plan.getDisplayName() + " Plan"));

            ItemCost costA = new ItemCost(Items.EMERALD, emeraldCost);
            // maxUses=3, xp=5, priceMult=0.05f
            return new MerchantOffer(costA, planPaper, 3, 5, 0.05f);

        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to build plan offer for level {}: {}", Main.MOD_ID, level, e.getMessage());
            return null;
        }
    }
}
