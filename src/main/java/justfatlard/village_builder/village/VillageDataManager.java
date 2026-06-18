package justfatlard.village_builder.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.integration.BuilderMailRegistration;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.util.BuildersTableFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
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

   private Map<BlockPos, VillageData> villages = new HashMap<>();
   private VillageDataState persistentState;
   private final ConstructionOrchestrator orchestrator = new ConstructionOrchestrator(this);
   private int tickCounter = 0;
   private final Set<BlockPos> notifiedThisCycle = new HashSet<>();
   private boolean wasDawn = false;

   // --- Lifecycle ---

   public void initialize(ServerLevel world) {
      this.persistentState = VillageDataState.getOrCreate(world);
      this.villages = this.persistentState.getVillages();
      this.persistentState.setVillages(this.villages);
      this.tickCounter = 0;
      this.notifiedThisCycle.clear();

      int orphansRemoved = 0;
      int orphansMigrated = 0;
      List<BlockPos> toRemove = new ArrayList<>();
      Map<BlockPos, BlockPos> toMigrate = new HashMap<>();

      for (BlockPos center : new ArrayList<>(this.villages.keySet())) {
         if (world.hasChunkAt(center) && world.getBlockState(center).getBlock() != Main.BUILDERS_TABLE_BLOCK) {
            BlockPos nearby = BuildersTableFinder.findNearestBuildersTable(world, center, FUZZY_MATCH_RADIUS);
            if (nearby != null) toMigrate.put(center, nearby);
            else toRemove.add(center);
         }
      }

      for (Entry<BlockPos, BlockPos> entry : toMigrate.entrySet()) {
         VillageData data = this.villages.remove(entry.getKey());
         if (data != null) {
            data.setVillageCenter(entry.getValue());
            this.villages.put(entry.getValue(), data);
            orphansMigrated++;
         }
      }
      for (BlockPos pos : toRemove) {
         this.villages.remove(pos);
         orphansRemoved++;
      }

      if (orphansRemoved > 0 || orphansMigrated > 0) {
         this.persistentState.setDirty();
         if (orphansRemoved > 0) LOGGER.info("Removed {} orphaned village entries on load", orphansRemoved);
         if (orphansMigrated > 0) LOGGER.info("Migrated {} village entries to nearby tables on load", orphansMigrated);
      }
      LOGGER.info("Loaded {} villages from persistent state", this.villages.size());
   }

   public void reset() {
      this.villages.clear();
      this.persistentState = null;
      this.orchestrator.reset();
      this.tickCounter = 0;
      this.notifiedThisCycle.clear();
      this.wasDawn = false;
      BuilderMailRegistration.reset();
      VillageBuilderAPI.clearListeners();
   }

   // --- Tick ---

   public void tick(ServerLevel world) {
      this.tickCounter++;

      if (this.tickCounter % CLEANUP_INTERVAL == 0) {
         this.cleanupOrphanedVillages(world);
         for (VillageData vd : this.villages.values()) {
            int pruned = vd.pruneAbsentFootprints(world);
            if (pruned > 0) {
               LOGGER.debug("Pruned {} stale footprints from village at {}", pruned, vd.getVillageCenter());
               this.persistentState.setDirty();
            }
         }
         this.orchestrator.expireStaleEntries(world.getServer().getTickCount());
      }

      if (this.tickCounter % TICK_INTERVAL == 0) {
         long timeOfDay = world.getOverworldClockTime() % 24000L;
         boolean isDawn = timeOfDay >= 23000L || timeOfDay <= 1000L;
         boolean dawnEdge = isDawn && !this.wasDawn;
         if (dawnEdge) this.notifiedThisCycle.clear();
         this.wasDawn = isDawn;

         int currentTick = world.getServer().getTickCount();
         for (VillageData villageData : new ArrayList<>(this.villages.values())) {
            BlockPos center = villageData.getVillageCenter();
            if (this.isConstructionReady(world, villageData, currentTick)) {
               if (isDawn) {
                  this.orchestrator.buildAtDawn(world, villageData);
               } else if (this.tickCounter % NOTIFICATION_INTERVAL == 0 && !this.notifiedThisCycle.contains(center)) {
                  this.notifyVillage(world, center, Component.translatable("message.village-builder.construction_ready"));
                  this.notifiedThisCycle.add(center);
               }
            } else if (dawnEdge) {
               this.orchestrator.gatherAtDawn(world, villageData);
            }
         }

         this.orchestrator.tickAdmireEvents(world, this.tickCounter);
      }
   }

   private boolean isConstructionReady(ServerLevel world, VillageData villageData, int currentTick) {
      BlockPos center = villageData.getVillageCenter();
      if (!world.hasChunkAt(center)) return false;
      if (villageData.getCurrentPlan() == null) return false;
      if (!villageData.hasAllMaterials()) return false;
      if (!this.orchestrator.hasBuilder(world, center)) return false;
      return !this.orchestrator.hasCooldown(center, currentTick);
   }

   // --- Village resolution ---

   public VillageData getVillageData(ServerLevel world, BlockPos pos) {
      BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(world, pos, VILLAGE_RADIUS);
      if (tablePos == null) {
         LOGGER.debug("No builder's table found near {} — cannot resolve village", pos);
         return null;
      }
      return this.resolveVillageData(world, tablePos);
   }

   public VillageData getVillageDataForTable(ServerLevel world, BlockPos tablePos) {
      return this.resolveVillageData(world, tablePos);
   }

   public VillageData getExistingVillageData(ServerLevel world, BlockPos pos) {
      BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(world, pos, VILLAGE_RADIUS);
      if (tablePos == null) return null;
      return this.villages.get(tablePos);
   }

   public boolean hasOtherTablesInVillage(ServerLevel world, BlockPos brokenTablePos) {
      BlockPos nearby = BuildersTableFinder.findNearestBuildersTable(world, brokenTablePos, FUZZY_MATCH_RADIUS);
      return nearby != null && !nearby.equals(brokenTablePos)
         && world.hasChunkAt(nearby)
         && world.getBlockState(nearby).getBlock() == Main.BUILDERS_TABLE_BLOCK;
   }

   private VillageData resolveVillageData(ServerLevel world, BlockPos tablePos) {
      VillageData existing = this.villages.get(tablePos);
      if (existing != null) {
         if (existing.getCurrentPlan() == null) this.assignNextPlan(world, existing);
         return existing;
      }

      for (Entry<BlockPos, VillageData> entry : new ArrayList<>(this.villages.entrySet())) {
         if (entry.getKey().distSqr(tablePos) < (double) (FUZZY_MATCH_RADIUS * FUZZY_MATCH_RADIUS)) {
            VillageData data = this.villages.remove(entry.getKey());
            this.orchestrator.migrateCenter(entry.getKey(), tablePos);
            data.setVillageCenter(tablePos);
            data.clearAnalyzer();
            this.villages.put(tablePos, data);
            this.markPersistentDirty();
            LOGGER.info("Migrated village data from {} to {}", entry.getKey(), tablePos);
            if (data.getCurrentPlan() == null) this.assignNextPlan(world, data);
            return data;
         }
      }

      VillageData data = new VillageData(tablePos);
      this.assignNextPlan(world, data);
      this.villages.put(tablePos, data);
      this.markPersistentDirty();
      return data;
   }

   // --- Material & plan management ---

   public void addMaterialToVillage(ServerLevel world, BlockPos tablePos, Item item, int count) {
      VillageData villageData = this.resolveVillageData(world, tablePos);
      if (villageData == null) {
         LOGGER.warn("Cannot add materials — no village found near {}", tablePos);
         return;
      }

      int overflow = villageData.tryAddMaterial(item, count);
      if (this.persistentState != null) this.persistentState.setDirty();

      int accepted = count - overflow;
      StructurePlan plan = villageData.getCurrentPlan();
      if (plan != null && accepted > 0) {
         float completion = villageData.getCompletionPercentage() * 100.0F;
         String itemName = Component.translatable(item.getDescriptionId()).getString();
         this.notifyVillage(world, tablePos,
            Component.translatable("message.village-builder.materials_added", accepted, itemName, String.format("%.1f", completion)));
      }

      if (overflow > 0) {
         ItemStack overflowStack = new ItemStack(item, overflow);
         world.addFreshEntity(new ItemEntity(
            world, tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5, overflowStack));
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
      if (this.persistentState != null) this.persistentState.setDirty();
      this.notifyVillage(world, tablePos, Component.translatable("message.village-builder.plan_set", plan.getDisplayName()));
      VillageBuilderAPI.firePlanChanged(world, villageData.getVillageCenter(), plan.getDisplayName());
      LOGGER.debug("Village plan set to {} at {}{}", plan.getDisplayName(), villageData.getVillageCenter(),
         patronName != null ? " (chosen by " + patronName + ")" : "");
   }

   // --- Callbacks for ConstructionOrchestrator ---

   public void markPersistentDirty() {
      if (this.persistentState != null) this.persistentState.setDirty();
   }

   void assignNextPlan(ServerLevel world, VillageData villageData) {
      VillageNeedsAnalyzer analyzer = villageData.getOrCreateAnalyzer(world);
      StructurePlan recommended = analyzer.getRecommendedPlan();
      if (recommended != null) {
         villageData.setCurrentPlan(recommended);
         if (this.persistentState != null) this.persistentState.setDirty();
         this.notifyVillage(world, villageData.getVillageCenter(),
            Component.translatable("message.village-builder.next_project", recommended.getDisplayName()));
         LOGGER.debug("Auto-assigned plan based on needs: {}", recommended.getDisplayName());
      }
   }

   void notifyVillage(ServerLevel world, BlockPos center, Component message) {
      this.notifyPlayersInRadius(world, center, VILLAGE_RADIUS, message);
   }

   void notifyGathering(ServerLevel world, BlockPos center, Component message) {
      this.notifyPlayersInRadius(world, center, GATHERING_NOTIFICATION_RADIUS, message);
   }

   private void notifyPlayersInRadius(ServerLevel world, BlockPos center, int radius, Component message) {
      world.players().forEach(player -> {
         if (player.blockPosition().closerThan(center, radius)) player.sendSystemMessage(message);
      });
   }

   // --- Queries ---

   public int getVillageCount() {
      return this.villages.size();
   }

   public int getBuiltStructureCount(BlockPos villageCenter) {
      VillageData data = this.villages.get(villageCenter);
      return data != null ? data.getBuiltStructures().size() : 0;
   }

   // --- Biome ---

   static String getVillageBiome(ServerLevel world, BlockPos center) {
      Holder<Biome> biomeEntry = world.getBiome(center);
      Optional<ResourceKey<Biome>> biomeKey = biomeEntry.unwrapKey();
      if (biomeKey.isEmpty()) return "plains";
      String path = biomeKey.get().identifier().getPath();
      if (path.contains("desert") || path.contains("badlands")) return "desert";
      if (path.contains("snowy") || path.contains("ice") || path.contains("frozen")) return "snowy";
      if (path.contains("taiga") || path.contains("grove") || path.contains("old_growth")) return "taiga";
      if (path.contains("savanna")) return "savanna";
      return "plains";
   }

   // --- Cleanup ---

   private void cleanupOrphanedVillages(ServerLevel world) {
      List<BlockPos> toRemove = new ArrayList<>();
      Map<BlockPos, BlockPos> toMigrate = new HashMap<>();

      for (Entry<BlockPos, VillageData> entry : this.villages.entrySet()) {
         BlockPos center = entry.getKey();
         if (!world.hasChunkAt(center)) continue;
         if (world.getBlockState(center).getBlock() == Main.BUILDERS_TABLE_BLOCK) continue;

         BlockPos replacement = BuildersTableFinder.findNearestBuildersTable(world, center, FUZZY_MATCH_RADIUS);
         if (replacement != null && !this.villages.containsKey(replacement) && !toMigrate.containsValue(replacement)) {
            toMigrate.put(center, replacement);
         } else {
            toRemove.add(center);
         }
      }

      for (BlockPos pos : toRemove) {
         this.villages.remove(pos);
         this.orchestrator.removeCenter(pos);
         LOGGER.info("Removed orphaned village at {} (table destroyed)", pos);
      }

      for (Entry<BlockPos, BlockPos> migration : toMigrate.entrySet()) {
         BlockPos oldCenter = migration.getKey();
         BlockPos newCenter = migration.getValue();
         VillageData data = this.villages.remove(oldCenter);
         if (data != null) {
            data.setVillageCenter(newCenter);
            data.clearAnalyzer();
            this.villages.put(newCenter, data);
            this.orchestrator.migrateCenter(oldCenter, newCenter);
            LOGGER.info("Migrated village center from {} to {} (original table destroyed)", oldCenter, newCenter);
         }
      }

      if (!toRemove.isEmpty() || !toMigrate.isEmpty()) this.markPersistentDirty();
   }
}
