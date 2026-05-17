package justfatlard.village_builder.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import justfatlard.village_builder.BuilderTrades;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.integration.BuilderMailRegistration;
import justfatlard.village_builder.integration.VillageQuestsIntegration;
import justfatlard.village_builder.util.BuildersTableFinder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageDataManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int NOTIFICATION_INTERVAL = 1200;
   private static final int TICK_INTERVAL = 20;
   private static final int CLEANUP_INTERVAL = 6000;
   private static final int VILLAGE_RADIUS = 64;
   private static final int GATHERING_NOTIFICATION_RADIUS = 32;
   private static final int FUZZY_MATCH_RADIUS = 16;
   private static final int EVICTION_AGE_TICKS = 12000;
   private static final int BUILD_COOLDOWN_TICKS = 2400;
   private static final Map<Item, Integer> GATHER_RATES = Map.ofEntries(
      Map.entry(Items.COBBLESTONE, 12),
      Map.entry(Items.STONE, 12),
      Map.entry(Items.DIRT, 12),
      Map.entry(Items.SANDSTONE, 10),
      Map.entry(Items.TERRACOTTA, 8),
      Map.entry(Items.WHITE_WOOL, 6),
      Map.entry(Items.OAK_LOG, 6),
      Map.entry(Items.SPRUCE_LOG, 6),
      Map.entry(Items.BIRCH_LOG, 6),
      Map.entry(Items.DARK_OAK_LOG, 6),
      Map.entry(Items.JUNGLE_LOG, 6),
      Map.entry(Items.ACACIA_LOG, 6),
      Map.entry(Items.MANGROVE_LOG, 6),
      Map.entry(Items.CHERRY_LOG, 6),
      Map.entry(Items.BAMBOO_BLOCK, 6),
      Map.entry(Items.CRIMSON_STEM, 6),
      Map.entry(Items.WARPED_STEM, 6),
      Map.entry(Items.COAL, 5),
      Map.entry(Items.GLASS, 4),
      Map.entry(Items.IRON_INGOT, 2),
      Map.entry(Items.LEATHER, 2),
      Map.entry(Items.GOLD_INGOT, 1),
      Map.entry(Items.DIAMOND, 1)
   );
   private Map<BlockPos, VillageData> villageDataCache = new HashMap<>();
   private Map<BlockPos, VillageData> persistentVillages = new HashMap<>();
   private VillageDataState persistentState;
   private int tickCounter = 0;
   private final Map<BlockPos, Integer> buildCooldowns = new HashMap<>();
   private final Set<BlockPos> notifiedThisCycle = new HashSet<>();
   private boolean wasDawn = false;
   private final Map<BlockPos, long[]> builderCountCache = new HashMap<>();
   private static final int BUILDER_CACHE_TICKS = 100;
   private static final int BUILDER_CACHE_JITTER = 40;
   private final Map<BlockPos, Long> admireEvents = new HashMap<>();
   private static final int ADMIRE_DURATION_TICKS = 900;
   private static final int ADMIRE_NAV_INTERVAL = 30;

   private static int getGatherRate(Item item) {
      return GATHER_RATES.getOrDefault(item, 1);
   }

   public void markPersistentDirty() {
      if (this.persistentState != null) {
         this.persistentState.setDirty();
      }
   }

   public void initialize(ServerLevel world) {
      this.persistentState = VillageDataState.getOrCreate(world);
      Map<BlockPos, VillageData> loaded = this.persistentState.getVillages();
      this.persistentVillages = new HashMap<>(loaded);
      this.villageDataCache = new HashMap<>(loaded);
      this.persistentState.setVillages(this.persistentVillages);
      this.tickCounter = 0;
      this.notifiedThisCycle.clear();
      int orphansRemoved = 0;
      int orphansMigrated = 0;
      ArrayList<BlockPos> toRemove = new ArrayList<>();
      HashMap<BlockPos, BlockPos> toMigrate = new HashMap<>();

      for (BlockPos center : new ArrayList<>(this.persistentVillages.keySet())) {
         if (world.hasChunkAt(center) && world.getBlockState(center).getBlock() != Main.BUILDERS_TABLE_BLOCK) {
            BlockPos nearbyTable = BuildersTableFinder.findNearestBuildersTable(world, center, 64);
            if (nearbyTable != null) {
               toMigrate.put(center, nearbyTable);
            } else {
               toRemove.add(center);
            }
         }
      }

      for (Entry<BlockPos, BlockPos> entry : toMigrate.entrySet()) {
         VillageData data = this.persistentVillages.remove(entry.getKey());
         this.villageDataCache.remove(entry.getKey());
         if (data != null) {
            data.setVillageCenter(entry.getValue());
            this.persistentVillages.put(entry.getValue(), data);
            this.villageDataCache.put(entry.getValue(), data);
            orphansMigrated++;
         }
      }

      for (BlockPos pos : toRemove) {
         this.persistentVillages.remove(pos);
         this.villageDataCache.remove(pos);
         orphansRemoved++;
      }

      if (orphansRemoved > 0 || orphansMigrated > 0) {
         this.persistentState.setDirty();
         if (orphansRemoved > 0) {
            LOGGER.info("Removed {} orphaned village entries on load", orphansRemoved);
         }
         if (orphansMigrated > 0) {
            LOGGER.info("Migrated {} village entries to nearby tables on load", orphansMigrated);
         }
      }

      LOGGER.info("Loaded {} villages from persistent state", this.villageDataCache.size());
   }

   public void reset() {
      this.villageDataCache = new HashMap<>();
      this.persistentVillages = new HashMap<>();
      this.persistentState = null;
      this.tickCounter = 0;
      this.notifiedThisCycle.clear();
      this.buildCooldowns.clear();
      this.wasDawn = false;
      this.builderCountCache.clear();
      this.admireEvents.clear();
      BuilderMailRegistration.reset();
      VillageBuilderAPI.clearListeners();
   }

   public int getVillageCount() {
      return this.persistentVillages.size();
   }

   public int getBuiltStructureCount(BlockPos villageCenter) {
      VillageData data = this.persistentVillages.get(villageCenter);
      if (data == null) {
         data = this.villageDataCache.get(villageCenter);
      }
      return data != null ? data.getBuiltStructures().size() : 0;
   }

   public VillageData getVillageData(ServerLevel world, BlockPos pos) {
      BlockPos villageCenter = BuildersTableFinder.findNearestBuildersTable(world, pos, 64);
      if (villageCenter == null) {
         LOGGER.debug("No builder's table found near {} — cannot resolve village", pos);
         return null;
      }
      return this.resolveVillageData(world, villageCenter);
   }

   public VillageData getVillageDataForTable(ServerLevel world, BlockPos tablePos) {
      return this.resolveVillageData(world, tablePos);
   }

   public VillageData getExistingVillageData(ServerLevel world, BlockPos pos) {
      BlockPos villageCenter = BuildersTableFinder.findNearestBuildersTable(world, pos, 64);
      if (villageCenter == null) {
         return null;
      }
      VillageData cached = this.villageDataCache.get(villageCenter);
      return cached != null ? cached : this.persistentVillages.get(villageCenter);
   }

   public boolean hasOtherTablesInVillage(ServerLevel world, BlockPos brokenTablePos) {
      VillageData data = this.getExistingVillageData(world, brokenTablePos);
      if (data == null) {
         return false;
      }
      for (Entry<BlockPos, VillageData> entry : this.villageDataCache.entrySet()) {
         if (entry.getValue() == data && !entry.getKey().equals(brokenTablePos)) {
            BlockPos otherPos = entry.getKey();
            if (world.hasChunkAt(otherPos) && world.getBlockState(otherPos).getBlock() == Main.BUILDERS_TABLE_BLOCK) {
               return true;
            }
         }
      }
      return false;
   }

   private VillageData resolveVillageData(ServerLevel world, BlockPos villageCenter) {
      long currentTick = world.getServer().getTickCount();
      VillageData existing = this.villageDataCache.get(villageCenter);
      if (existing != null) {
         existing.touch(currentTick);
         return existing;
      }

      VillageData persisted = this.persistentVillages.get(villageCenter);
      if (persisted != null) {
         persisted.touch(currentTick);
         persisted.clearAnalyzer();
         this.villageDataCache.put(villageCenter, persisted);
         if (persisted.getCurrentPlan() == null) {
            this.assignPlanBasedOnNeeds(world, persisted);
         }
         return persisted;
      }

      // Check for nearby village data to migrate
      BlockPos migrateFrom = null;
      double closestDist = Double.MAX_VALUE;
      for (Entry<BlockPos, VillageData> entry : this.persistentVillages.entrySet()) {
         double dist = entry.getKey().distSqr(villageCenter);
         if (dist < 256.0 && dist < closestDist) {
            migrateFrom = entry.getKey();
            closestDist = dist;
         }
      }

      if (migrateFrom != null) {
         VillageData migrated = this.persistentVillages.remove(migrateFrom);
         this.villageDataCache.remove(migrateFrom);
         migrated.setVillageCenter(villageCenter);
         migrated.clearAnalyzer();
         migrated.touch(currentTick);
         this.villageDataCache.put(villageCenter, migrated);
         this.persistentVillages.put(villageCenter, migrated);
         if (this.persistentState != null) {
            this.persistentState.setDirty();
         }
         LOGGER.info("Migrated village data from {} to {}", migrateFrom, villageCenter);
         return migrated;
      }

      // Check for village within wider radius to share data
      for (Entry<BlockPos, VillageData> entry : this.persistentVillages.entrySet()) {
         double dist = entry.getKey().distSqr(villageCenter);
         if (dist < 4096.0) {
            VillageData nearby = entry.getValue();
            nearby.touch(currentTick);
            nearby.clearAnalyzer();
            this.villageDataCache.put(villageCenter, nearby);
            this.villageDataCache.put(entry.getKey(), nearby);
            return nearby;
         }
      }

      // Create new village data
      VillageData data = new VillageData(villageCenter);
      data.touch(currentTick);
      if (data.getCurrentPlan() == null) {
         this.assignPlanBasedOnNeeds(world, data);
      }
      this.villageDataCache.put(villageCenter, data);
      this.persistentVillages.put(villageCenter, data);
      if (this.persistentState != null) {
         this.persistentState.setDirty();
      }
      return data;
   }

   public void addMaterialToVillage(ServerLevel world, BlockPos tablePos, Item item, int count) {
      VillageData villageData = this.resolveVillageData(world, tablePos);
      if (villageData == null) {
         LOGGER.warn("Cannot add materials — no village found near {}", tablePos);
         return;
      }

      int overflow = villageData.tryAddMaterial(item, count);
      if (this.persistentState != null) {
         this.persistentState.setDirty();
      }

      int accepted = count - overflow;
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan != null && accepted > 0) {
         float completion = villageData.getCompletionPercentage() * 100.0F;
         String itemName = Component.translatable(item.getDescriptionId()).getString();
         this.notifyNearbyPlayers(
            world, tablePos,
            Component.translatable("message.village-builder.materials_added", accepted, itemName, String.format("%.1f", completion))
         );
      }

      if (overflow > 0) {
         ItemStack overflowStack = new ItemStack(item, overflow);
         ItemEntity itemEntity = new ItemEntity(
            world, tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5, overflowStack
         );
         world.addFreshEntity(itemEntity);
         world.players().forEach(player -> {
            if (player.blockPosition().closerThan(tablePos, 64.0)) {
               player.sendSystemMessage(Component.translatable("message.village-builder.inventory_full"));
            }
         });
      }

      LOGGER.debug("Added {} materials to village at {} ({} overflow)", accepted, villageData.getVillageCenter(), overflow);
   }

   public void setVillagePlan(ServerLevel world, BlockPos tablePos, StructurePlan plan) {
      this.setVillagePlan(world, tablePos, plan, null, null);
   }

   public void setVillagePlan(ServerLevel world, BlockPos tablePos, StructurePlan plan, UUID patronUuid, String patronName) {
      VillageData villageData = this.resolveVillageData(world, tablePos);
      if (villageData == null) {
         LOGGER.warn("Cannot set plan — no village found near {}", tablePos);
         return;
      }

      villageData.setCurrentPlan(plan);
      villageData.setPlanPatron(patronUuid, patronName);
      if (this.persistentState != null) {
         this.persistentState.setDirty();
      }

      this.notifyNearbyPlayers(world, tablePos, Component.translatable("message.village-builder.plan_set", plan.getDisplayName()));
      VillageBuilderAPI.firePlanChanged(world, villageData.getVillageCenter(), plan.getDisplayName());
      LOGGER.debug("Village plan set to {} at {}{}", plan.getDisplayName(), villageData.getVillageCenter(),
         patronName != null ? " (chosen by " + patronName + ")" : "");
   }

   private void assignPlanBasedOnNeeds(ServerLevel world, VillageData villageData) {
      VillageNeedsAnalyzer analyzer = villageData.getOrCreateAnalyzer(world);
      StructurePlan recommendedPlan = analyzer.getRecommendedPlan();
      if (recommendedPlan != null) {
         villageData.setCurrentPlan(recommendedPlan);
         if (this.persistentState != null) {
            this.persistentState.setDirty();
         }
         this.notifyNearbyPlayers(
            world, villageData.getVillageCenter(),
            Component.translatable("message.village-builder.next_project", recommendedPlan.getDisplayName())
         );
         LOGGER.debug("Auto-assigned plan based on needs: {}", recommendedPlan.getDisplayName());
      }
   }

   public void tick(ServerLevel world) {
      this.tickCounter++;

      if (this.tickCounter % CLEANUP_INTERVAL == 0) {
         this.cleanupOrphanedVillages(world);

         for (VillageData vd : this.persistentVillages.values()) {
            int pruned = vd.validateBuiltStructures(world);
            if (pruned > 0) {
               LOGGER.debug("Pruned {} stale built-structure records from village at {}", pruned, vd.getVillageCenter());
               if (this.persistentState != null) {
                  this.persistentState.setDirty();
               }
            }
         }

         int cleanTick = world.getServer().getTickCount();
         this.buildCooldowns.entrySet().removeIf(entry -> cleanTick >= entry.getValue());
         this.builderCountCache.entrySet().removeIf(entry -> cleanTick >= entry.getValue()[1]);
      }

      if (this.tickCounter % TICK_INTERVAL == 0) {
         long timeOfDay = world.getOverworldClockTime() % 24000L;
         boolean isDawn = timeOfDay >= 23000L || timeOfDay <= 1000L;
         boolean dawnEdge = isDawn && !this.wasDawn;
         if (dawnEdge) {
            this.notifiedThisCycle.clear();
         }
         this.wasDawn = isDawn;

         int currentTick = world.getServer().getTickCount();
         Set<VillageData> seen = new HashSet<>();
         List<VillageData> uniqueVillages = new ArrayList<>();
         for (VillageData v : this.villageDataCache.values()) {
            if (seen.add(v)) {
               uniqueVillages.add(v);
            }
         }

         for (VillageData villageData : uniqueVillages) {
            BlockPos center = villageData.getVillageCenter();
            if (this.isConstructionReady(world, villageData, currentTick)) {
               if (isDawn) {
                  this.buildAndGatherVillagers(world, villageData);
               } else if (this.tickCounter % NOTIFICATION_INTERVAL == 0 && !this.notifiedThisCycle.contains(center)) {
                  this.notifyNearbyPlayers(world, center, Component.translatable("message.village-builder.construction_ready"));
                  this.notifiedThisCycle.add(center);
               }
            } else if (dawnEdge) {
               this.gatherMaterials(world, villageData);
            }
         }

         if (!this.admireEvents.isEmpty() && this.tickCounter % ADMIRE_NAV_INTERVAL == 0) {
            Iterator<Entry<BlockPos, Long>> admireIter = this.admireEvents.entrySet().iterator();
            while (admireIter.hasNext()) {
               Entry<BlockPos, Long> entry = admireIter.next();
               if (currentTick >= entry.getValue()) {
                  admireIter.remove();
               } else {
                  this.nudgeVillagersToward(world, entry.getKey());
               }
            }
         }
      }
   }

   private boolean isConstructionReady(ServerLevel world, VillageData villageData, int currentTick) {
      BlockPos center = villageData.getVillageCenter();
      if (!this.isVillageChunkLoaded(world, villageData)) return false;
      if (villageData.getCurrentPlan() == null) return false;
      if (!villageData.hasAllMaterials()) return false;
      if (!this.hasBuilderInVillage(world, center)) return false;
      Integer cooldownExpiry = this.buildCooldowns.get(center);
      return cooldownExpiry == null || currentTick >= cooldownExpiry;
   }

   private boolean isVillageChunkLoaded(ServerLevel world, VillageData villageData) {
      if (world.hasChunkAt(villageData.getVillageCenter())) {
         return true;
      }
      for (Entry<BlockPos, VillageData> entry : this.villageDataCache.entrySet()) {
         if (entry.getValue() == villageData && world.hasChunkAt(entry.getKey())) {
            return true;
         }
      }
      return false;
   }

   private boolean hasBuilderInVillage(ServerLevel world, BlockPos center) {
      return this.countBuildersInVillage(world, center) > 0;
   }

   private int countBuildersInVillage(ServerLevel world, BlockPos center) {
      long currentTick = world.getServer().getTickCount();
      long[] cached = this.builderCountCache.get(center);
      if (cached != null && currentTick < cached[1]) {
         return (int) cached[0];
      }
      AABB searchBox = new AABB(center).inflate(64.0);
      int count = world.getEntitiesOfClass(Villager.class, searchBox,
         villager -> villager.getVillagerData().profession().is(Main.BUILDER_KEY)).size();
      int jitter = (center.hashCode() & Integer.MAX_VALUE) % BUILDER_CACHE_JITTER;
      this.builderCountCache.put(center, new long[]{count, currentTick + BUILDER_CACHE_TICKS + jitter});
      return count;
   }

   private void gatherMaterials(ServerLevel world, VillageData villageData) {
      BlockPos center = villageData.getVillageCenter();
      if (!world.hasChunkAt(center)) return;
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan == null) return;
      int builderCount = this.countBuildersInVillage(world, center);
      if (builderCount == 0) return;

      int gathered;
      int overflow;
      String targetItemKey;
      float completion;

      synchronized (villageData.getInventoryStacks()) {
         List<StructureType.MaterialRequirement> needed = new ArrayList<>();
         for (StructureType.MaterialRequirement req : plan.getRequirements()) {
            int have = villageData.getMaterialCount(req.item());
            if (have < req.amount()) {
               needed.add(req);
            }
         }
         if (needed.isEmpty()) return;

         int index = villageData.getGatheringIndex() % needed.size();
         StructureType.MaterialRequirement target = needed.get(index);
         villageData.advanceGatheringIndex();
         int baseRate = getGatherRate(target.item());
         int amount = baseRate * builderCount;
         int remaining = target.amount() - villageData.getMaterialCount(target.item());
         amount = Math.min(amount, remaining);
         if (amount <= 0) return;

         overflow = villageData.tryAddMaterial(target.item(), amount);
         gathered = amount - overflow;
         targetItemKey = target.item().getDescriptionId();
         completion = villageData.getCompletionPercentage() * 100.0F;
      }

      if (gathered > 0) {
         if (this.persistentState != null) {
            this.persistentState.setDirty();
         }
         this.notifyPlayersInRadius(world, center, GATHERING_NOTIFICATION_RADIUS,
            Component.translatable("message.village-builder.builders_gathered",
               gathered, Component.translatable(targetItemKey).getString(), String.format("%.0f", completion)));
         if (overflow > 0) {
            this.notifyPlayersInRadius(world, center, GATHERING_NOTIFICATION_RADIUS,
               Component.translatable("message.village-builder.inventory_full"));
         }
         LOGGER.debug("Village at {} gathered {} materials ({} builder{}, {} overflow)",
            center, gathered, builderCount, builderCount > 1 ? "s" : "", overflow);
      }
   }

   private void buildAndGatherVillagers(ServerLevel world, VillageData villageData) {
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan == null) return;

      BlockPos center = villageData.getVillageCenter();
      this.builderCountCache.remove(center);
      if (!this.hasBuilderInVillage(world, center)) {
         LOGGER.debug("Builder no longer present at {} — construction deferred", center);
         return;
      }

      StructureEntry registryEntry = Main.STRUCTURE_REGISTRY.get(plan.getStructureId());
      StructureType resolvedType = StructureType.fromId(plan.getStructureId().getPath());
      int clearanceSize;
      if (registryEntry != null) {
         clearanceSize = registryEntry.clearanceSize();
      } else if (resolvedType != null) {
         clearanceSize = resolvedType.getFootprintSize();
      } else {
         LOGGER.error("Unknown structure '{}' in plan — reassigning", plan.getStructureId());
         villageData.clearCurrentPlan();
         villageData.clearAnalyzer();
         if (this.persistentState != null) {
            this.persistentState.setDirty();
         }
         this.assignPlanBasedOnNeeds(world, villageData);
         return;
      }

      List<ItemStack> inventorySnapshot = villageData.snapshotAndConsumeMaterials();
      if (inventorySnapshot == null) {
         LOGGER.debug("Materials no longer sufficient for {} — construction deferred", plan.getDisplayName());
         return;
      }

      BlockPos buildPos = this.findBuildingSpot(world, villageData, clearanceSize);
      if (buildPos == null) {
         villageData.restoreInventory(inventorySnapshot);
         villageData.incrementPlacementFailures();
         this.buildCooldowns.put(villageData.getVillageCenter(), world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
         LOGGER.debug("Placement failed for {} at village {} (clearance={}, built={}/{}): no valid spot found in 40 attempts",
            plan.getDisplayName(), villageData.getVillageCenter(), clearanceSize,
            villageData.getBuiltStructures().size(), VillageData.MAX_BUILT_STRUCTURES);

         if (villageData.getPlacementFailures() >= VillageData.MAX_PLACEMENT_FAILURES) {
            if (villageData.getBuiltStructures().size() >= 35) {
               LOGGER.info("Village at {} appears full ({} structures) — pausing construction",
                  villageData.getVillageCenter(), villageData.getBuiltStructures().size());
               villageData.resetPlacementFailures();
               villageData.clearCurrentPlan();
               if (this.persistentState != null) this.persistentState.setDirty();
               this.notifyNearbyPlayers(world, villageData.getVillageCenter(),
                  Component.translatable("message.village-builder.village_full"));
            } else {
               LOGGER.warn("Plan {} failed placement {} times — reassigning",
                  plan.getDisplayName(), villageData.getPlacementFailures());
               villageData.resetPlacementFailures();
               villageData.clearCurrentPlan();
               villageData.clearAnalyzer();
               if (this.persistentState != null) this.persistentState.setDirty();
               this.assignPlanBasedOnNeeds(world, villageData);
               this.notifyNearbyPlayers(world, villageData.getVillageCenter(),
                  Component.translatable("message.village-builder.plan_changed_no_space"));
            }
         } else {
            LOGGER.warn("No build location found for {} (attempt {}/{}) — materials restored, cooldown applied",
               plan.getDisplayName(), villageData.getPlacementFailures(), VillageData.MAX_PLACEMENT_FAILURES);
            this.notifyNearbyPlayers(world, villageData.getVillageCenter(),
               Component.translatable("message.village-builder.no_build_location"));
         }
         return;
      }

      boolean built;
      if (resolvedType != null) {
         built = Main.BUILDING_MANAGER.buildStructure(world, buildPos, resolvedType, villageData.getVillageCenter());
      } else {
         built = Main.BUILDING_MANAGER.placeTemplate(world, buildPos, plan.getStructureId(), villageData.getVillageCenter());
      }

      if (!built) {
         villageData.restoreInventory(inventorySnapshot);
         this.buildCooldowns.put(villageData.getVillageCenter(), world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
         this.notifyNearbyPlayers(world, villageData.getVillageCenter(),
            Component.translatable("message.village-builder.build_failed"));
         LOGGER.warn("Failed to build {} at {} — {} (materials restored, cooldown applied)",
            plan.getDisplayName(), buildPos,
            resolvedType != null ? "hardcoded blueprint placement failed"
               : "NBT template '" + plan.getStructureId() + "' could not be placed (template missing or corrupted?)");
         return;
      }

      // Construction successful
      String villageName = VillageQuestsIntegration.getVillageName(villageData.getVillageCenter());
      String patronName = villageData.getPlanPatronName();
      Component announcement;
      if (patronName != null && villageName != null) {
         announcement = Component.translatable("message.village-builder.building_constructed_patron",
            plan.getDisplayName(), villageName, patronName);
      } else if (villageName != null) {
         announcement = Component.translatable("message.village-builder.building_constructed_named",
            plan.getDisplayName(), villageName);
      } else {
         announcement = Component.translatable("message.village-builder.building_constructed",
            plan.getDisplayName());
      }

      world.players().forEach(player -> {
         if (player.blockPosition().closerThan(buildPos, 64.0)) {
            player.sendSystemMessage(announcement);
         }
      });

      world.playSound(null, buildPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.3F, 1.2F);
      world.playSound(null, buildPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
      world.playSound(null, buildPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5F, 0.8F);
      LOGGER.info("Built {} at dawn at {}", plan.getDisplayName(), buildPos);

      VillageBuilderAPI.fireConstructionComplete(world, villageData.getVillageCenter(), plan.getDisplayName(), buildPos);

      boolean offTheme = false;
      StructureEntry entry = Main.STRUCTURE_REGISTRY.get(plan.getStructureId());
      if (entry != null && !entry.biomePreferences().isEmpty()) {
         String villageBiome = getVillageBiome(world, villageData.getVillageCenter());
         offTheme = !entry.fitsInBiome(villageBiome);
      }

      BuilderMailRegistration.notifyConstructionComplete(
         world.getServer(), villageData.getVillageCenter(), plan.getDisplayName(),
         villageName, villageData.getPlanPatronUuid(), patronName, offTheme);
      BuilderMailRegistration.checkMilestoneMail(
         world.getServer(), villageData.getVillageCenter(), villageData.getBuiltStructures().size() + 1);

      int totalBuilt = villageData.getBuiltStructures().size() + 1;
      world.players().forEach(player -> {
         if (player.blockPosition().closerThan(buildPos, 64.0)) {
            grantAdvancement(world, player, "first_build");
            if (totalBuilt >= 5) {
               grantAdvancement(world, player, "five_builds");
            }
            if (totalBuilt >= 10) {
               grantAdvancement(world, player, "ten_builds");
            }
         }
      });

      this.gatherVillagersToNewBuilding(world, buildPos);
      villageData.recordBuiltStructure(buildPos, clearanceSize);
      villageData.resetPlacementFailures();
      villageData.clearCurrentPlan();
      villageData.clearAnalyzer();
      this.buildCooldowns.put(villageData.getVillageCenter(), world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
      if (this.persistentState != null) {
         this.persistentState.setDirty();
      }
      this.assignPlanBasedOnNeeds(world, villageData);
      BuilderTrades.refreshVillagerOffers(world, villageData.getVillageCenter());
   }

   private BlockPos findBuildingSpot(ServerLevel world, VillageData villageData, int clearanceSize) {
      BlockPos center = villageData.getVillageCenter();
      RandomSource random = world.getRandom();
      int verticalClearance = Math.max(4, clearanceSize / 2);
      BlockPos bestPos = null;
      double bestDist = Double.MAX_VALUE;
      WorldBorder worldBorder = world.getWorldBorder();

      for (int attempt = 0; attempt < 40; attempt++) {
         int radius = attempt < 15 ? 16 + attempt : 24 + attempt;
         radius = Math.min(radius, 48);
         double angle = random.nextFloat() * Math.PI * 2.0;
         double dist = (random.nextFloat() * 0.7 + 0.3) * radius;
         int x = center.getX() + (int) (Math.cos(angle) * dist);
         int z = center.getZ() + (int) (Math.sin(angle) * dist);
         if (worldBorder.isWithinBounds(new BlockPos(x, 0, z))
             && worldBorder.isWithinBounds(new BlockPos(x + clearanceSize, 0, z + clearanceSize))) {
            int surfaceY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos testPos = new BlockPos(x, surfaceY, z);
            if (!villageData.overlapsExistingStructure(testPos, clearanceSize)) {
               boolean clear = true;
               for (int dx = 0; dx < clearanceSize && clear; dx++) {
                  for (int dz = 0; dz < clearanceSize && clear; dz++) {
                     BlockPos checkPos = testPos.offset(dx, 0, dz);
                     if (!world.getBlockState(checkPos.below()).isSolid()) {
                        clear = false;
                        break;
                     }
                     for (int dy = 0; dy < verticalClearance && clear; dy++) {
                        BlockPos airCheck = testPos.offset(dx, dy, dz);
                        BlockState blockState = world.getBlockState(airCheck);
                        if (!blockState.isAir() && !blockState.canBeReplaced() && !isVegetationOrSnow(blockState)) {
                           clear = false;
                        }
                     }
                  }
               }
               if (clear) {
                  double distToCenter = testPos.distSqr(center);
                  if (distToCenter < bestDist) {
                     bestDist = distToCenter;
                     bestPos = testPos;
                  }
                  if (distToCenter < 400.0) {
                     return testPos;
                  }
               }
            }
         }
      }
      return bestPos;
   }

   private static boolean isVegetationOrSnow(BlockState state) {
      Block block = state.getBlock();
      return block instanceof VegetationBlock || block instanceof MushroomBlock || block == Blocks.SNOW;
   }

   private void gatherVillagersToNewBuilding(ServerLevel world, BlockPos buildingPos) {
      long expiryTick = world.getServer().getTickCount() + ADMIRE_DURATION_TICKS;
      this.admireEvents.put(buildingPos, expiryTick);
      this.nudgeVillagersToward(world, buildingPos);
   }

   private void nudgeVillagersToward(ServerLevel world, BlockPos buildingPos) {
      AABB searchBox = new AABB(buildingPos).inflate(48.0);
      List<Villager> villagers = world.getEntitiesOfClass(Villager.class, searchBox, v -> true);
      RandomSource random = world.getRandom();

      for (Villager villager : villagers) {
         double distSq = villager.distanceToSqr(buildingPos.getX(), buildingPos.getY(), buildingPos.getZ());
         if (distSq < 25.0) {
            villager.getLookControl().setLookAt(buildingPos.getX() + 0.5, buildingPos.getY() + 2, buildingPos.getZ() + 0.5);
         } else {
            int distance = random.nextInt(4) + 3;
            double angle = random.nextFloat() * Math.PI * 2.0;
            int targetX = buildingPos.getX() + (int) (Math.cos(angle) * distance);
            int targetZ = buildingPos.getZ() + (int) (Math.sin(angle) * distance);
            BlockPos targetPos = new BlockPos(targetX, buildingPos.getY(), targetZ);

            while (targetPos.getY() > world.getMinY() && world.getBlockState(targetPos.below()).isAir()) {
               targetPos = targetPos.below();
            }
            while (targetPos.getY() < world.getMaxY() - 1 && !world.getBlockState(targetPos).isAir()) {
               targetPos = targetPos.above();
            }

            villager.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.5);
            villager.getLookControl().setLookAt(buildingPos.getX() + 0.5, buildingPos.getY() + 2, buildingPos.getZ() + 0.5);
         }
      }
   }

   private static void grantAdvancement(ServerLevel world, ServerPlayer player, String advancementPath) {
      AdvancementHolder advancementEntry = world.getServer().getAdvancements().get(
         Identifier.fromNamespaceAndPath("village-builder", advancementPath));
      if (advancementEntry != null) {
         AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancementEntry);
         if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
               player.getAdvancements().award(advancementEntry, criterion);
            }
         }
      }
   }

   static String getVillageBiome(ServerLevel world, BlockPos center) {
      Holder<Biome> biomeEntry = world.getBiome(center);
      Optional<ResourceKey<Biome>> biomeKey = biomeEntry.unwrapKey();
      if (biomeKey.isEmpty()) {
         return "plains";
      }
      String path = biomeKey.get().identifier().getPath();
      if (path.contains("plains") || path.contains("meadow") || path.contains("sunflower")) {
         return "plains";
      } else if (path.contains("taiga") || path.contains("grove") || path.contains("old_growth")) {
         return "taiga";
      } else if (path.contains("desert")) {
         return "desert";
      } else if (path.contains("savanna") || path.contains("bamboo") || path.contains("jungle")) {
         return "savanna";
      } else if (path.contains("snowy") || path.contains("ice") || path.contains("frozen")) {
         return "snowy";
      } else if (path.contains("cherry")) {
         return "plains";
      } else {
         return "plains";
      }
   }

   private void notifyNearbyPlayers(ServerLevel world, BlockPos center, Component message) {
      this.notifyPlayersInRadius(world, center, VILLAGE_RADIUS, message);
   }

   private void notifyPlayersInRadius(ServerLevel world, BlockPos center, int radius, Component message) {
      world.players().forEach(player -> {
         if (player.blockPosition().closerThan(center, radius)) {
            player.sendSystemMessage(message);
         }
      });
   }

   private void cleanupOrphanedVillages(ServerLevel world) {
      long currentTick = world.getServer().getTickCount();
      ArrayList<BlockPos> toEvictFromCache = new ArrayList<>();
      ArrayList<BlockPos> toRemoveFromCache = new ArrayList<>();
      Set<BlockPos> confirmedOrphans = new HashSet<>();

      for (Entry<BlockPos, VillageData> entry : this.villageDataCache.entrySet()) {
         BlockPos center = entry.getKey();
         VillageData data = entry.getValue();
         if (world.hasChunkAt(center)) {
            if (world.getBlockState(center).getBlock() != Main.BUILDERS_TABLE_BLOCK) {
               confirmedOrphans.add(center);
            }
         } else if (currentTick - data.getLastAccessedTick() > EVICTION_AGE_TICKS) {
            toEvictFromCache.add(center);
         }
      }

      Set<VillageData> fullyOrphaned = new HashSet<>();
      Map<VillageData, BlockPos> migrateTargets = new HashMap<>();

      for (BlockPos orphanPos : confirmedOrphans) {
         VillageData data = this.villageDataCache.get(orphanPos);
         if (data != null) {
            BlockPos validAlias = null;
            for (Entry<BlockPos, VillageData> entry : this.villageDataCache.entrySet()) {
               if (entry.getValue() == data && !confirmedOrphans.contains(entry.getKey())) {
                  validAlias = entry.getKey();
                  break;
               }
            }
            toRemoveFromCache.add(orphanPos);
            if (validAlias == null) {
               fullyOrphaned.add(data);
            } else if (orphanPos.equals(data.getVillageCenter())) {
               migrateTargets.put(data, validAlias);
            }
         }
      }

      for (BlockPos pos : toEvictFromCache) {
         this.villageDataCache.remove(pos);
         LOGGER.debug("Evicted village at {} from cache (persistent data preserved)", pos);
      }

      for (BlockPos pos : toRemoveFromCache) {
         this.villageDataCache.remove(pos);
         VillageData data = this.persistentVillages.get(pos);
         if (data != null && fullyOrphaned.contains(data)) {
            this.persistentVillages.remove(pos);
            LOGGER.info("Removed orphaned village data at {} (table destroyed)", pos);
         } else if (data != null) {
            LOGGER.debug("Removed alias cache key at {} (canonical table still exists)", pos);
         }
      }

      for (Entry<VillageData, BlockPos> entry : migrateTargets.entrySet()) {
         VillageData data = entry.getKey();
         BlockPos oldCenter = data.getVillageCenter();
         BlockPos newCenter = entry.getValue();
         this.persistentVillages.remove(oldCenter);
         data.setVillageCenter(newCenter);
         this.persistentVillages.put(newCenter, data);
         this.villageDataCache.put(newCenter, data);
         Integer cooldown = this.buildCooldowns.remove(oldCenter);
         if (cooldown != null) {
            this.buildCooldowns.put(newCenter, cooldown);
         }
         long[] bcount = this.builderCountCache.remove(oldCenter);
         if (bcount != null) {
            this.builderCountCache.put(newCenter, bcount);
         }
         LOGGER.info("Migrated village center from {} to {} (original table destroyed)", oldCenter, newCenter);
      }

      if ((!fullyOrphaned.isEmpty() || !migrateTargets.isEmpty()) && this.persistentState != null) {
         this.persistentState.setDirty();
      }
   }
}
