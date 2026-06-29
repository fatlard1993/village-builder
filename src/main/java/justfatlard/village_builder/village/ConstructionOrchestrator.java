package justfatlard.village_builder.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import justfatlard.village_builder.BuilderTrades;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.integration.BuilderMailRegistration;
import justfatlard.village_builder.integration.VillageQuestsIntegration;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

class ConstructionOrchestrator {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");

   private static final int BUILD_COOLDOWN_TICKS = 2400;
   private static final int ADMIRE_DURATION_TICKS = 900;
   private static final int ADMIRE_NAV_INTERVAL = 30;
   private static final int BUILDER_CACHE_TICKS = 100;
   private static final int BUILDER_CACHE_JITTER = 40;

   private static final Map<Item, Integer> GATHER_RATES = Map.ofEntries(
      Map.entry(Items.COBBLESTONE, 12),
      Map.entry(Items.STONE, 12),
      Map.entry(Items.DIRT, 12),
      Map.entry(Items.SANDSTONE, 10),
      Map.entry(Items.TERRACOTTA, 8),
      Map.entry(Items.WOOL.pick(DyeColor.WHITE), 6),
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
      Map.entry(Items.STONE_BRICKS, 8),
      Map.entry(Items.BRICKS, 6),
      Map.entry(Items.SAND, 12),
      Map.entry(Items.GLASS_PANE, 4),
      Map.entry(Items.TUFF_BRICKS, 8),
      Map.entry(Items.DEEPSLATE_BRICKS, 6),
      Map.entry(Items.COPPER_BLOCK.weathering().unaffected(), 3),
      Map.entry(Items.GLOWSTONE, 2),
      Map.entry(Items.CHEST, 2),
      Map.entry(Items.PUMPKIN, 3),
      Map.entry(Items.PUMPKIN_SEEDS, 4),
      Map.entry(Items.BONE_MEAL, 4)
   );

   private final VillageDataManager manager;
   private final Map<BlockPos, Integer> buildCooldowns = new HashMap<>();
   private final Map<BlockPos, Long> admireEvents = new HashMap<>();
   private final Map<BlockPos, long[]> builderCountCache = new HashMap<>();

   ConstructionOrchestrator(VillageDataManager manager) {
      this.manager = manager;
   }

   // --- Public interface called by VillageDataManager ---

   boolean hasCooldown(BlockPos center, int currentTick) {
      Integer expiry = this.buildCooldowns.get(center);
      return expiry != null && currentTick < expiry;
   }

   boolean hasBuilder(ServerLevel world, BlockPos center) {
      return this.countBuilders(world, center) > 0;
   }

   int countBuilders(ServerLevel world, BlockPos center) {
      long currentTick = world.getServer().getTickCount();
      long[] cached = this.builderCountCache.get(center);
      if (cached != null && currentTick < cached[1]) return (int) cached[0];
      AABB searchBox = new AABB(center).inflate(64.0);
      int count = world.getEntitiesOfClass(Villager.class, searchBox,
         v -> v.getVillagerData().profession().is(Main.BUILDER_KEY)).size();
      int jitter = (center.hashCode() & Integer.MAX_VALUE) % BUILDER_CACHE_JITTER;
      this.builderCountCache.put(center, new long[]{count, currentTick + BUILDER_CACHE_TICKS + jitter});
      return count;
   }

   void gatherAtDawn(ServerLevel world, VillageData villageData) {
      BlockPos center = villageData.getVillageCenter();
      if (!world.hasChunkAt(center)) return;
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan == null) return;
      int builderCount = this.countBuilders(world, center);
      if (builderCount == 0) return;

      List<StructureType.MaterialRequirement> needed = new ArrayList<>();
      for (StructureType.MaterialRequirement req : plan.getRequirements()) {
         if (villageData.getMaterialCount(req.item()) < req.amount()) needed.add(req);
      }
      if (needed.isEmpty()) return;

      int index = villageData.getGatheringIndex() % needed.size();
      StructureType.MaterialRequirement target = needed.get(index);
      villageData.advanceGatheringIndex();
      int amount = Math.min(
         gatherRate(target.item()) * builderCount,
         target.amount() - villageData.getMaterialCount(target.item())
      );
      if (amount <= 0) return;

      int overflow = villageData.tryAddMaterial(target.item(), amount);
      int gathered = amount - overflow;
      String targetItemKey = target.item().getDescriptionId();
      float completion = villageData.getCompletionPercentage() * 100.0F;

      if (gathered > 0) {
         manager.markPersistentDirty();
         manager.notifyGathering(world, center,
            Component.translatable("message.village-builder.builders_gathered",
               gathered, Component.translatable(targetItemKey).getString(), String.format("%.0f", completion)));
         if (overflow > 0) {
            manager.notifyGathering(world, center,
               Component.translatable("message.village-builder.inventory_full"));
         }
         LOGGER.debug("Village at {} gathered {} {} ({} builder{}, {} overflow)",
            center, gathered, targetItemKey, builderCount, builderCount > 1 ? "s" : "", overflow);
      }
   }

   void buildAtDawn(ServerLevel world, VillageData villageData) {
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan == null) return;

      BlockPos center = villageData.getVillageCenter();
      this.builderCountCache.remove(center); // force fresh count before building
      if (!this.hasBuilder(world, center)) {
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
         manager.markPersistentDirty();
         manager.assignNextPlan(world, villageData);
         return;
      }

      List<ItemStack> snapshot = villageData.snapshotAndConsumeMaterials();
      if (snapshot == null) {
         LOGGER.debug("Materials no longer sufficient for {} — construction deferred", plan.getDisplayName());
         return;
      }

      BlockPos buildPos = this.findBuildingSpot(world, villageData, clearanceSize);
      if (buildPos == null) {
         villageData.restoreInventory(snapshot);
         villageData.incrementPlacementFailures();
         this.buildCooldowns.put(center, world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
         LOGGER.debug("Placement failed for {} at village {} (clearance={}, built={}/{}): no valid spot found",
            plan.getDisplayName(), center, clearanceSize,
            villageData.getBuiltStructures().size(), VillageData.MAX_BUILT_STRUCTURES);

         if (villageData.getPlacementFailures() >= VillageData.MAX_PLACEMENT_FAILURES) {
            if (villageData.getBuiltStructures().size() >= 35) {
               LOGGER.info("Village at {} appears full ({} structures) — pausing construction",
                  center, villageData.getBuiltStructures().size());
               villageData.resetPlacementFailures();
               villageData.clearCurrentPlan();
               manager.markPersistentDirty();
               manager.notifyVillage(world, center, Component.translatable("message.village-builder.village_full"));
            } else {
               LOGGER.warn("Plan {} failed placement {} times — reassigning",
                  plan.getDisplayName(), villageData.getPlacementFailures());
               villageData.resetPlacementFailures();
               villageData.clearCurrentPlan();
               villageData.clearAnalyzer();
               manager.markPersistentDirty();
               manager.assignNextPlan(world, villageData);
               manager.notifyVillage(world, center,
                  Component.translatable("message.village-builder.plan_changed_no_space"));
            }
         } else {
            LOGGER.warn("No build location for {} (attempt {}/{}) — materials restored, cooldown applied",
               plan.getDisplayName(), villageData.getPlacementFailures(), VillageData.MAX_PLACEMENT_FAILURES);
            manager.notifyVillage(world, center,
               Component.translatable("message.village-builder.no_build_location"));
         }
         return;
      }

      boolean built = resolvedType != null
         ? Main.BUILDING_MANAGER.buildStructure(world, buildPos, resolvedType, center)
         : Main.BUILDING_MANAGER.placeTemplate(world, buildPos, plan.getStructureId(), center);

      if (!built) {
         villageData.restoreInventory(snapshot);
         this.buildCooldowns.put(center, world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
         manager.notifyVillage(world, center, Component.translatable("message.village-builder.build_failed"));
         LOGGER.warn("Failed to build {} at {} — {} (materials restored, cooldown applied)",
            plan.getDisplayName(), buildPos,
            resolvedType != null ? "hardcoded blueprint failed"
               : "template '" + plan.getStructureId() + "' missing or corrupted");
         return;
      }

      // Construction successful
      String villageName = VillageQuestsIntegration.getVillageName(center);
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
      manager.notifyVillage(world, center, announcement);

      world.playSound(null, buildPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.3F, 1.2F);
      world.playSound(null, buildPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
      world.playSound(null, buildPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5F, 0.8F);
      LOGGER.info("Built {} at dawn at {}", plan.getDisplayName(), buildPos);

      VillageBuilderAPI.fireConstructionComplete(world, center, plan.getDisplayName(), buildPos);

      boolean offTheme = false;
      StructureEntry entry = Main.STRUCTURE_REGISTRY.get(plan.getStructureId());
      if (entry != null && !entry.biomePreferences().isEmpty()) {
         offTheme = !entry.fitsInBiome(VillageDataManager.getVillageBiome(world, center));
      }

      int nextBuildCount = villageData.getBuiltStructures().size() + 1;
      BuilderMailRegistration.notifyConstructionComplete(
         world.getServer(), center, plan.getDisplayName(),
         villageName, villageData.getPlanPatronUuid(), patronName, offTheme, nextBuildCount);
      BuilderMailRegistration.checkMilestoneMail(world.getServer(), center, nextBuildCount);

      world.players().forEach(player -> {
         if (player.blockPosition().closerThan(buildPos, 64.0)) {
            grantAdvancement(world, player, "first_build");
            if (nextBuildCount >= 5) grantAdvancement(world, player, "five_builds");
            if (nextBuildCount >= 10) grantAdvancement(world, player, "ten_builds");
         }
      });

      this.celebrateNewBuilding(world, buildPos);
      villageData.recordBuiltStructure(buildPos, clearanceSize);
      this.relocateWorkbench(world, villageData, buildPos);
      villageData.resetPlacementFailures();
      villageData.clearCurrentPlan();
      villageData.clearAnalyzer();
      this.buildCooldowns.put(center, world.getServer().getTickCount() + BUILD_COOLDOWN_TICKS);
      manager.markPersistentDirty();
      manager.assignNextPlan(world, villageData);
      BuilderTrades.refreshVillagerOffers(world, center);
   }

   void tickAdmireEvents(ServerLevel world, int tickCounter) {
      if (this.admireEvents.isEmpty()) return;
      if (tickCounter % ADMIRE_NAV_INTERVAL != 0) return;
      long currentTick = world.getServer().getTickCount();
      Iterator<Entry<BlockPos, Long>> iter = this.admireEvents.entrySet().iterator();
      while (iter.hasNext()) {
         Entry<BlockPos, Long> entry = iter.next();
         if (currentTick >= entry.getValue()) {
            iter.remove();
         } else {
            this.nudgeVillagersToward(world, entry.getKey());
         }
      }
   }

   void migrateCenter(BlockPos oldCenter, BlockPos newCenter) {
      Integer cooldown = this.buildCooldowns.remove(oldCenter);
      if (cooldown != null) this.buildCooldowns.put(newCenter, cooldown);
      long[] bcount = this.builderCountCache.remove(oldCenter);
      if (bcount != null) this.builderCountCache.put(newCenter, bcount);
   }

   void removeCenter(BlockPos center) {
      this.buildCooldowns.remove(center);
      this.builderCountCache.remove(center);
   }

   void expireStaleEntries(int currentTick) {
      this.buildCooldowns.entrySet().removeIf(e -> currentTick >= e.getValue());
      long now = currentTick;
      this.builderCountCache.entrySet().removeIf(e -> now >= e.getValue()[1]);
   }

   void reset() {
      this.buildCooldowns.clear();
      this.admireEvents.clear();
      this.builderCountCache.clear();
   }

   // --- Private implementation ---

   /**
    * After a successful construction, physically move the Builders Table block
    * to a position near the new build site. This signals to players where the
    * next construction will be — builders move from worksite to worksite.
    */
   private void relocateWorkbench(ServerLevel world, VillageData villageData, BlockPos buildPos) {
      BlockPos oldCenter = villageData.getVillageCenter();
      RandomSource random = world.getRandom();

      // Find a clear surface spot 8-15 blocks from the build site
      BlockPos newCenter = null;
      for (int attempt = 0; attempt < 24; attempt++) {
         double angle = random.nextFloat() * Math.PI * 2.0;
         int dist = 8 + random.nextInt(8); // 8-15 blocks away
         int x = buildPos.getX() + (int)(Math.cos(angle) * dist);
         int z = buildPos.getZ() + (int)(Math.sin(angle) * dist);
         int y = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos candidate = new BlockPos(x, y, z);
         if (world.getBlockState(candidate).isAir()
               && world.getBlockState(candidate.below()).isSolid()
               && !villageData.overlapsExistingStructure(candidate, 2)) {
            newCenter = candidate;
            break;
         }
      }
      if (newCenter == null) {
         LOGGER.debug("Could not find relocation spot near {} — workbench stays at {}", buildPos, oldCenter);
         return;
      }

      // Move the physical block
      world.removeBlock(oldCenter, false);
      world.setBlock(newCenter, Main.BUILDERS_TABLE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

      // Update in-memory structures immediately (POI scan will confirm on next tick)
      this.migrateCenter(oldCenter, newCenter);
      villageData.setVillageCenter(newCenter);

      LOGGER.info("Workbench relocated from {} to {} near new build at {}", oldCenter, newCenter, buildPos);
   }

   private BlockPos findBuildingSpot(ServerLevel world, VillageData villageData, int clearanceSize) {
      BlockPos center = villageData.getVillageCenter();
      RandomSource random = world.getRandom();
      int verticalClearance = Math.max(4, clearanceSize / 2);
      WorldBorder worldBorder = world.getWorldBorder();

      BlockPos preferred = villageData.getPreferredBuildSite();
      villageData.clearPreferredBuildSite();
      if (preferred != null) {
         int surfaceY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, preferred.getX(), preferred.getZ());
         BlockPos testPos = new BlockPos(preferred.getX(), surfaceY, preferred.getZ());
         if (worldBorder.isWithinBounds(testPos)
               && worldBorder.isWithinBounds(new BlockPos(preferred.getX() + clearanceSize, 0, preferred.getZ() + clearanceSize))
               && !villageData.overlapsExistingStructure(testPos, clearanceSize)
               && isSpotClear(world, testPos, clearanceSize, verticalClearance)) {
            LOGGER.debug("Using player-designated build site at {}", testPos);
            return testPos;
         }
         LOGGER.debug("Designated build site at {} was invalid — falling back to random search", preferred);
      }

      BlockPos bestPos = null;
      double bestDist = Double.MAX_VALUE;
      for (int attempt = 0; attempt < 40; attempt++) {
         int radius = Math.min(attempt < 15 ? 16 + attempt : 24 + attempt, 48);
         double angle = random.nextFloat() * Math.PI * 2.0;
         double dist = (random.nextFloat() * 0.7 + 0.3) * radius;
         int x = center.getX() + (int) (Math.cos(angle) * dist);
         int z = center.getZ() + (int) (Math.sin(angle) * dist);
         if (worldBorder.isWithinBounds(new BlockPos(x, 0, z))
               && worldBorder.isWithinBounds(new BlockPos(x + clearanceSize, 0, z + clearanceSize))) {
            int surfaceY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos testPos = new BlockPos(x, surfaceY, z);
            if (!villageData.overlapsExistingStructure(testPos, clearanceSize)
                  && isSpotClear(world, testPos, clearanceSize, verticalClearance)) {
               double distToCenter = testPos.distSqr(center);
               if (distToCenter < bestDist) {
                  bestDist = distToCenter;
                  bestPos = testPos;
               }
               if (distToCenter < 400.0) return testPos;
            }
         }
      }
      return bestPos;
   }

   private static boolean isSpotClear(ServerLevel world, BlockPos pos, int size, int verticalClearance) {
      for (int dx = 0; dx < size; dx++) {
         for (int dz = 0; dz < size; dz++) {
            if (!world.getBlockState(pos.offset(dx, -1, dz)).isSolid()) return false;
            for (int dy = 0; dy < verticalClearance; dy++) {
               BlockState s = world.getBlockState(pos.offset(dx, dy, dz));
               if (!s.isAir() && !s.canBeReplaced() && !isVegetationOrSnow(s)) return false;
            }
         }
      }
      return true;
   }

   private static boolean isVegetationOrSnow(BlockState state) {
      Block block = state.getBlock();
      return block instanceof VegetationBlock || block instanceof MushroomBlock || block == Blocks.SNOW;
   }

   private void celebrateNewBuilding(ServerLevel world, BlockPos buildingPos) {
      long expiryTick = world.getServer().getTickCount() + ADMIRE_DURATION_TICKS;
      this.admireEvents.put(buildingPos, expiryTick);
      this.nudgeVillagersToward(world, buildingPos);
   }

   private void nudgeVillagersToward(ServerLevel world, BlockPos buildingPos) {
      List<Villager> villagers = world.getEntitiesOfClass(Villager.class,
         new AABB(buildingPos).inflate(48.0), v -> true);
      RandomSource random = world.getRandom();
      for (Villager villager : villagers) {
         if (villager.distanceToSqr(buildingPos.getX(), buildingPos.getY(), buildingPos.getZ()) < 25.0) {
            villager.getLookControl().setLookAt(buildingPos.getX() + 0.5, buildingPos.getY() + 2, buildingPos.getZ() + 0.5);
         } else {
            double angle = random.nextFloat() * Math.PI * 2.0;
            int distance = random.nextInt(4) + 3;
            int tx = buildingPos.getX() + (int) (Math.cos(angle) * distance);
            int tz = buildingPos.getZ() + (int) (Math.sin(angle) * distance);
            BlockPos target = new BlockPos(tx, buildingPos.getY(), tz);
            while (target.getY() > world.getMinY() && world.getBlockState(target.below()).isAir()) target = target.below();
            while (target.getY() < world.getMaxY() - 1 && !world.getBlockState(target).isAir()) target = target.above();
            villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.5);
            villager.getLookControl().setLookAt(buildingPos.getX() + 0.5, buildingPos.getY() + 2, buildingPos.getZ() + 0.5);
         }
      }
   }

   private static void grantAdvancement(ServerLevel world, ServerPlayer player, String advancementPath) {
      AdvancementHolder holder = world.getServer().getAdvancements().get(
         Identifier.fromNamespaceAndPath("village-builder", advancementPath));
      if (holder != null) {
         AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
         if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
               player.getAdvancements().award(holder, criterion);
            }
         }
      }
   }

   private static int gatherRate(Item item) {
      return GATHER_RATES.getOrDefault(item, 1);
   }
}
