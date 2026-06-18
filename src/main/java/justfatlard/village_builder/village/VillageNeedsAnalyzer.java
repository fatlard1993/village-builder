package justfatlard.village_builder.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureRegistry;
import justfatlard.village_builder.building.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageNeedsAnalyzer {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final long CACHE_DURATION_TICKS = 1200L;
   private static final int SCAN_RADIUS = 32;
   private static final float BED_OCCUPANCY_THRESHOLD = 0.75F;
   private static final float FOOD_PER_VILLAGER_THRESHOLD = 10.0F;
   private static final int FARMLAND_PER_VILLAGER = 2;
   private static final float PROFESSION_DIVERSITY_THRESHOLD = 0.3F;
   private static final int DEFENSE_POPULATION_THRESHOLD = 10;
   private static final int GOLEM_TO_VILLAGER_RATIO = 10;
   private static final int UTILITY_POPULATION_THRESHOLD = 5;
   private static final float DIVERSITY_TARGET_PROFESSIONS = 5.0F;
   private final ServerLevel world;
   private final BlockPos villageCenter;
   private int villagerCount = 0;
   private int bedCount = 0;
   private int foodSupply = 0;
   private Map<VillagerProfession, Integer> professionCounts = new HashMap<>();
   private int farmlandCount = 0;
   private boolean hasWell = false;
   private int ironGolemCount = 0;
   private long lastMetricsTickTime = -1L;

   public VillageNeedsAnalyzer(ServerLevel world, BlockPos villageCenter) {
      this.world = world;
      this.villageCenter = villageCenter;
   }

   public VillageNeedsAnalyzer.VillageNeed analyzeNeeds() {
      return this.analyzeNeedsByPriority().get(0);
   }

   public List<VillageNeedsAnalyzer.VillageNeed> analyzeNeedsByPriority() {
      this.gatherMetrics();
      List<VillageNeedsAnalyzer.VillageNeed> priorities = new ArrayList<>();
      float bedOccupancy = this.villagerCount > 0 ? (float)this.villagerCount / Math.max(this.bedCount, 1) : 0.0F;
      float foodPerVillager = this.villagerCount > 0 ? (float)this.foodSupply / this.villagerCount : 100.0F;
      if (this.villagerCount == 0 && this.bedCount == 0) {
         priorities.add(VillageNeedsAnalyzer.VillageNeed.HOUSING);
         priorities.add(VillageNeedsAnalyzer.VillageNeed.FOOD);
         priorities.add(VillageNeedsAnalyzer.VillageNeed.UTILITY);
         priorities.add(VillageNeedsAnalyzer.VillageNeed.PROFESSION);
         priorities.add(VillageNeedsAnalyzer.VillageNeed.DEFENSE);
         priorities.add(VillageNeedsAnalyzer.VillageNeed.PROSPERITY);
         return priorities;
      } else {
         if (bedOccupancy > 0.75F) {
            priorities.add(VillageNeedsAnalyzer.VillageNeed.HOUSING);
         }

         if (foodPerVillager < 10.0F || this.farmlandCount < this.villagerCount * 2) {
            priorities.add(VillageNeedsAnalyzer.VillageNeed.FOOD);
         }

         if (this.getProfessionDiversity() < 0.3F) {
            priorities.add(VillageNeedsAnalyzer.VillageNeed.PROFESSION);
         }

         if (this.villagerCount > 10 && this.ironGolemCount < this.villagerCount / 10) {
            priorities.add(VillageNeedsAnalyzer.VillageNeed.DEFENSE);
         }

         if (!this.hasWell && this.villagerCount > 5) {
            priorities.add(VillageNeedsAnalyzer.VillageNeed.UTILITY);
         }

         for (VillageNeedsAnalyzer.VillageNeed need : VillageNeedsAnalyzer.VillageNeed.values()) {
            if (!priorities.contains(need)) {
               priorities.add(need);
            }
         }

         return priorities;
      }
   }

   public List<StructurePlan> getRecommendedPlans(VillageNeedsAnalyzer.VillageNeed need) {
      String biomeKey = this.getBiomeKey();
      StructureRegistry registry = Main.STRUCTURE_REGISTRY;
      int builderCount = this.countBuilders();
      List<StructureEntry> entries = registry.query(need, biomeKey);
      if (entries.isEmpty()) {
         entries = registry.query(need);
      }

      List<StructurePlan> plans = new ArrayList<>();

      for (StructureEntry entry : entries) {
         if (entry.minBuildersRequired() <= builderCount) {
            plans.add(new StructurePlan(entry));
         }
      }

      if (plans.isEmpty() && !entries.isEmpty()) {
         int minRequired = Integer.MAX_VALUE;

         for (StructureEntry entryx : entries) {
            minRequired = Math.min(minRequired, entryx.minBuildersRequired());
         }

         for (StructureEntry entryx : entries) {
            if (entryx.minBuildersRequired() == minRequired) {
               plans.add(new StructurePlan(entryx));
            }
         }
      }

      return plans;
   }

   public StructurePlan getRecommendedPlan() {
      List<VillageNeedsAnalyzer.VillageNeed> priorities = this.analyzeNeedsByPriority();
      int builderCount = this.countBuilders();
      VillageNeedsAnalyzer.VillageNeed topNeed = priorities.get(0);
      if (this.isBuilderBottlenecked(topNeed, builderCount)) {
         String biomeKey = this.getBiomeKey();
         StructureRegistry registry = Main.STRUCTURE_REGISTRY;
         List<StructureEntry> profEntries = registry.query(VillageNeedsAnalyzer.VillageNeed.PROFESSION, biomeKey);
         if (profEntries.isEmpty()) {
            profEntries = registry.query(VillageNeedsAnalyzer.VillageNeed.PROFESSION);
         }

         for (StructureEntry entry : profEntries) {
            if (entry.id().getPath().contains("builders_workshop")) {
               return new StructurePlan(entry);
            }
         }

         if (!profEntries.isEmpty()) {
            return new StructurePlan(profEntries.get(this.world.getRandom().nextInt(profEntries.size())));
         }
      }

      for (VillageNeedsAnalyzer.VillageNeed need : priorities) {
         List<StructurePlan> plans = this.getRecommendedPlans(need);
         if (!plans.isEmpty()) {
            return plans.get(this.world.getRandom().nextInt(plans.size()));
         }
      }

      return new StructurePlan(StructureType.BUILDERS_WORKSHOP);
   }

   private boolean isBuilderBottlenecked(VillageNeedsAnalyzer.VillageNeed need, int builderCount) {
      String biomeKey = this.getBiomeKey();
      StructureRegistry registry = Main.STRUCTURE_REGISTRY;
      List<StructureEntry> entries = registry.query(need, biomeKey);
      if (entries.isEmpty()) {
         entries = registry.query(need);
      }

      if (entries.isEmpty()) {
         return false;
      } else {
         for (StructureEntry entry : entries) {
            if (entry.minBuildersRequired() <= builderCount) {
               return false;
            }
         }

         return true;
      }
   }

   private int countBuilders() {
      AABB searchBox = new AABB(this.villageCenter).inflate(64.0);
      return this.world.getEntitiesOfClass(Villager.class, searchBox, v -> v.getVillagerData().profession().is(Main.BUILDER_KEY)).size();
   }

   private String getBiomeKey() {
      return VillageDataManager.getVillageBiome(this.world, this.villageCenter);
   }

   private void gatherMetrics() {
      long currentTick = this.world.getServer().getTickCount();
      if (this.lastMetricsTickTime < 0L || currentTick - this.lastMetricsTickTime >= 1200L) {
         this.lastMetricsTickTime = currentTick;
         this.villagerCount = 0;
         this.bedCount = 0;
         this.foodSupply = 0;
         this.professionCounts.clear();
         this.farmlandCount = 0;
         this.hasWell = false;
         this.ironGolemCount = 0;
         AABB searchBox = new AABB(this.villageCenter).inflate(32.0);
         List<Villager> villagers = this.world.getEntitiesOfClass(Villager.class, searchBox, v -> true);
         this.villagerCount = villagers.size();

         for (Villager villager : villagers) {
            VillagerProfession profession = villager.getVillagerData().profession().value();
            this.professionCounts.merge(profession, 1, Integer::sum);
         }

         this.ironGolemCount = this.world.getEntitiesOfClass(IronGolem.class, searchBox, g -> true).size();
         MutableBlockPos mutablePos = new MutableBlockPos();
         MutableBlockPos wellCheckPos = new MutableBlockPos();
         int sampleStep = 2;
         int sampledFarmlandCount = 0;
         int sampledFoodSupply = 0;

         int sampledBedCount = 0;
         for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
               if (x * x + z * z <= 1024) {
                  mutablePos.set(this.villageCenter.getX() + x, this.villageCenter.getY(), this.villageCenter.getZ() + z);
                  if (this.world.hasChunkAt(mutablePos)) {
                     for (int y = -10; y <= 20; y++) {
                        mutablePos.set(
                           this.villageCenter.getX() + x, this.villageCenter.getY() + y, this.villageCenter.getZ() + z
                        );
                        BlockState state = this.world.getBlockState(mutablePos);
                        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                           sampledBedCount++;
                        }
                     }
                  }
               }
            }
         }
         this.bedCount = sampledBedCount;

         for (int x = -32; x <= 32; x += sampleStep) {
            for (int zx = -32; zx <= 32; zx += sampleStep) {
               if (x * x + zx * zx <= 1024) {
                  mutablePos.set(this.villageCenter.getX() + x, this.villageCenter.getY(), this.villageCenter.getZ() + zx);
                  if (this.world.hasChunkAt(mutablePos)) {
                     for (int yx = -10; yx <= 20; yx++) {
                        mutablePos.set(
                           this.villageCenter.getX() + x, this.villageCenter.getY() + yx, this.villageCenter.getZ() + zx
                        );
                        BlockState state = this.world.getBlockState(mutablePos);
                        if (!state.isAir()) {
                           if (state.getBlock() == Blocks.FARMLAND) {
                              sampledFarmlandCount++;
                           } else if (state.getBlock() == Blocks.WATER && !this.hasWell) {
                              int stoneNeighbors = 0;
                              for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                 wellCheckPos.set(mutablePos.getX() + d[0], mutablePos.getY(), mutablePos.getZ() + d[1]);
                                 Block nb = this.world.getBlockState(wellCheckPos).getBlock();
                                 if (nb == Blocks.COBBLESTONE || nb == Blocks.STONE_BRICKS
                                       || nb == Blocks.MOSSY_COBBLESTONE || nb == Blocks.COBBLESTONE_WALL
                                       || nb == Blocks.STONE_BRICK_WALL) {
                                    stoneNeighbors++;
                                 }
                              }
                              if (stoneNeighbors >= 3) {
                                 this.hasWell = true;
                              }
                           } else if (state.getBlock() == Blocks.BARREL) {
                              if (this.world.getBlockEntity(mutablePos) instanceof BarrelBlockEntity barrel) {
                                 for (int i = 0; i < barrel.getContainerSize(); i++) {
                                    ItemStack stack = barrel.getItem(i);
                                    if (stack.has(DataComponents.FOOD)) {
                                       sampledFoodSupply += stack.getCount();
                                    }
                                 }
                              }
                           } else if (state.getBlock() instanceof CropBlock crop) {
                              int age = 0;
                              int maxAge = crop.getMaxAge();
                              if (state.getBlock() == Blocks.BEETROOTS) {
                                 age = (Integer)state.getValue(BeetrootBlock.AGE);
                              } else if (state.hasProperty(CropBlock.AGE)) {
                                 age = (Integer)state.getValue(CropBlock.AGE);
                              }

                              sampledFoodSupply += age == maxAge ? 2 : 1;
                           }
                        }
                     }
                  }
               }
            }
         }

         int scaleFactor = sampleStep * sampleStep;
         this.farmlandCount = sampledFarmlandCount * scaleFactor;
         this.foodSupply = sampledFoodSupply * scaleFactor;
         LOGGER.debug(
            "Village metrics - Villagers: {}, Beds: {}, Food: {}, Farms: {}",
            new Object[]{this.villagerCount, this.bedCount, this.foodSupply, this.farmlandCount}
         );
      }
   }

   public int getVillagerCount() {
      return this.villagerCount;
   }

   public int getBedCount() {
      return this.bedCount;
   }

   public int getFoodSupply() {
      return this.foodSupply;
   }

   public int getFarmlandCount() {
      return this.farmlandCount;
   }

   public int getIronGolemCount() {
      return this.ironGolemCount;
   }

   private float getProfessionDiversity() {
      if (this.villagerCount == 0) {
         return 0.0F;
      } else {
         int employedCount = 0;
         int uniqueProfessions = 0;

         for (Entry<VillagerProfession, Integer> e : this.professionCounts.entrySet()) {
            VillagerProfession prof = e.getKey();
            Optional<ResourceKey<VillagerProfession>> profKey = BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(prof);
            if (!profKey.isPresent() || !profKey.get().equals(VillagerProfession.NONE) && !profKey.get().equals(VillagerProfession.NITWIT)) {
               employedCount += e.getValue();
               uniqueProfessions++;
            }
         }

         float employedRatio = (float)employedCount / this.villagerCount;
         float diversity = Math.min(1.0F, uniqueProfessions / 5.0F);
         return (employedRatio + diversity) / 2.0F;
      }
   }

   public static enum VillageNeed {
      HOUSING,
      FOOD,
      PROFESSION,
      DEFENSE,
      UTILITY,
      PROSPERITY;
   }
}
