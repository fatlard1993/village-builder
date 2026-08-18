package justfatlard.village_builder.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import justfatlard.village_builder.BuilderTrades;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.MaterialMapping;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageBuilderAPI {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final List<VillageBuilderAPI.ConstructionListener> constructionListeners = new ArrayList<>();
   private static final List<VillageBuilderAPI.PlanChangedListener> planChangedListeners = new ArrayList<>();
   /**
    * Counters that report how many of a limit group a village already has, from sources Village
    * Builder did not build. Registered once at mod init and deliberately not cleared with the
    * listeners: a world reload does not re-run another mod's initializer.
    */
   private static final Map<String, VillageBuilderAPI.LimitGroupSeeder> limitGroupSeeders = new HashMap<>();
   public static final String BIOME_PLAINS = "plains";
   public static final String BIOME_TAIGA = "taiga";
   public static final String BIOME_DESERT = "desert";
   public static final String BIOME_SAVANNA = "savanna";
   public static final String BIOME_SNOWY = "snowy";

   public static void clearListeners() {
      constructionListeners.clear();
      planChangedListeners.clear();
   }

   public static void onConstructionComplete(VillageBuilderAPI.ConstructionListener listener) {
      constructionListeners.add(listener);
   }

   public static void onPlanChanged(VillageBuilderAPI.PlanChangedListener listener) {
      planChangedListeners.add(listener);
   }

   /**
    * Teach Village Builder to recognise structures of a limit group that it did not build itself.
    *
    * <p>Per-village limits count what the builder has completed. A structure that was already in
    * the world when the village was first surveyed, placed by worldgen or by hand, is invisible to
    * that count, so a village could hold one of yours and still be offered another. Register a
    * seeder and it is asked once, when the village's data is first created, how many of the group
    * are already present; the answer is folded into the village's tally before its first plan is
    * chosen.
    *
    * <p>Called on the server thread during village discovery, so keep it cheap: a structure lookup
    * or a short block scan, not a world sweep. Register from your mod's initializer.
    */
   public static void registerLimitGroupSeeder(String limitGroup, VillageBuilderAPI.LimitGroupSeeder seeder) {
      if (limitGroup == null || limitGroup.isBlank() || seeder == null) {
         return;
      }
      limitGroupSeeders.put(limitGroup, seeder);
      LOGGER.info("Registered limit group seeder for '{}'", limitGroup);
   }

   /**
    * Ask every registered seeder what this village already has, and record it.
    *
    * <p>Called by Village Builder when a village's data is created. A seeder that throws is logged
    * and skipped rather than aborting village discovery.
    */
   public static void seedLimitGroups(ServerLevel world, BlockPos villageCenter, VillageData data) {
      if (limitGroupSeeders.isEmpty()) {
         return;
      }

      for (Map.Entry<String, VillageBuilderAPI.LimitGroupSeeder> entry : limitGroupSeeders.entrySet()) {
         try {
            int existing = entry.getValue().countExisting(world, villageCenter);
            if (existing > 0) {
               data.seedLimitGroup(entry.getKey(), existing);
               LOGGER.info("Village at {} already has {} of limit group '{}'",
                  villageCenter, existing, entry.getKey());
            }
         } catch (Exception e) {
            LOGGER.warn("Limit group seeder for '{}' failed at {}: {}",
               entry.getKey(), villageCenter, e.getMessage());
         }
      }
   }

   public static void fireConstructionComplete(ServerLevel world, BlockPos villageCenter, String structureName, BlockPos buildPos) {
      for (VillageBuilderAPI.ConstructionListener listener : constructionListeners) {
         try {
            listener.onConstruction(world, villageCenter, structureName, buildPos);
         } catch (Exception var7) {
            LOGGER.error("Error in construction listener", var7);
         }
      }
   }

   public static void firePlanChanged(ServerLevel world, BlockPos villageCenter, String newPlanName) {
      for (VillageBuilderAPI.PlanChangedListener listener : planChangedListeners) {
         try {
            listener.onPlanChanged(world, villageCenter, newPlanName);
         } catch (Exception var6) {
            LOGGER.error("Error in plan changed listener", var6);
         }
      }
   }

   public static VillageBuilderAPI.DonationResult processDonatedMaterials(ServerLevel world, BlockPos donationPos, List<ItemStack> donatedItems) {
      List<ItemStack> accepted = new ArrayList<>();
      List<ItemStack> rejected = new ArrayList<>();
      int overflowLost = 0;
      if (donatedItems == null || donatedItems.isEmpty()) {
         return new VillageBuilderAPI.DonationResult(accepted, rejected, 0);
      }
      VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageData(world, donationPos);
      if (villageData == null) {
         rejected.addAll(donatedItems);
         return new VillageBuilderAPI.DonationResult(accepted, rejected, 0);
      } else {
         for (ItemStack donated : donatedItems) {
            if (donated == null || donated.isEmpty()) continue;
            Item donatedItem = donated.getItem();
            int donatedCount = donated.getCount();
            if (isBuildingMaterial(donatedItem)) {
               int overflow = villageData.tryAddMaterial(donatedItem, donatedCount);
               int actuallyAccepted = donatedCount - overflow;
               if (actuallyAccepted > 0) {
                  ItemStack acceptedStack = donated.copy();
                  acceptedStack.setCount(actuallyAccepted);
                  accepted.add(acceptedStack);
               }

               overflowLost += overflow;
            } else {
               rejected.add(donated.copy());
            }
         }

         if (!accepted.isEmpty()) {
            int totalAccepted = accepted.stream().mapToInt(ItemStack::getCount).sum();
            Component notificationText = Component.translatable("message.village-builder.donation_accepted", new Object[]{totalAccepted});
            world.players().forEach(player -> {
               if (player.blockPosition().closerThan(donationPos, 64.0)) {
                  player.sendSystemMessage(notificationText);
               }
            });
            LOGGER.info("Accepted {} building materials for village stockpile", totalAccepted);
         }

         if (overflowLost > 0) {
            LOGGER.warn("{} building material items lost to inventory overflow", overflowLost);
         }

         return new VillageBuilderAPI.DonationResult(accepted, rejected, overflowLost);
      }
   }

   public static boolean isBuildingMaterial(Item item) {
      return BuilderTrades.MATERIAL_POOL_SET.contains(item) || MaterialMapping.getAllMaterialItems().contains(item);
   }

   public static boolean isNeededForConstruction(ServerLevel world, BlockPos villagePos, Item item) {
      VillageData villageData = Main.VILLAGE_DATA_MANAGER.getExistingVillageData(world, villagePos);
      if (villageData != null && villageData.getCurrentPlan() != null) {
         StructurePlan currentPlan = villageData.getCurrentPlan();

         for (StructureType.MaterialRequirement req : currentPlan.getRequirements()) {
            if (req.item() == item) {
               int currentCount = villageData.getMaterialCount(item);
               return currentCount < req.amount();
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void registerStructure(
      Identifier id,
      String displayName,
      Set<VillageNeedsAnalyzer.VillageNeed> needs,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      StructureEntry entry = new StructureEntry(id, displayName, needs, requirements, biomePreferences, clearanceSize, StructureEntry.Source.MOD_REGISTERED);
      Main.STRUCTURE_REGISTRY.register(entry);
      LOGGER.info("Registered mod structure: {} ({})", id, needs);
   }

   public static void registerStructure(
      Identifier id,
      String displayName,
      VillageNeedsAnalyzer.VillageNeed need,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      registerStructure(id, displayName, Set.of(need), requirements, biomePreferences, clearanceSize);
   }

   public static void registerStructurePersistent(
      Identifier id,
      String displayName,
      Set<VillageNeedsAnalyzer.VillageNeed> needs,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      Runnable registration = () -> registerStructure(id, displayName, needs, requirements, biomePreferences, clearanceSize);
      Main.STRUCTURE_REGISTRY.addReloadCallback(registration);
      if (Main.STRUCTURE_REGISTRY.isInitialized()) {
         registration.run();
      }
   }

   public static void registerStructurePersistent(
      Identifier id,
      String displayName,
      VillageNeedsAnalyzer.VillageNeed need,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      registerStructurePersistent(id, displayName, Set.of(need), requirements, biomePreferences, clearanceSize);
   }

   /**
    * Register a structure that a village may only build a limited number of times.
    *
    * <p>Entries sharing a {@code limitGroup} count against one another, so a mod offering the same
    * building in several sizes can cap the whole family at one per village instead of one of each.
    * Pass the mod's own namespaced string as the group. A {@code maxPerVillage} of zero or less is
    * unlimited, which is what the plain overloads give you.
    *
    * <p>The count is per village and persists with the village's saved data; it is not reset by a
    * structure being destroyed, so a village that loses its keep does not immediately rebuild one.
    */
   public static void registerStructurePersistent(
      Identifier id,
      String displayName,
      Set<VillageNeedsAnalyzer.VillageNeed> needs,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize,
      String limitGroup,
      int maxPerVillage
   ) {
      Runnable registration = () -> {
         StructureEntry entry = new StructureEntry(
            id, displayName, needs, requirements, biomePreferences, clearanceSize,
            StructureEntry.Source.MOD_REGISTERED, limitGroup, maxPerVillage
         );
         Main.STRUCTURE_REGISTRY.register(entry);
         LOGGER.info("Registered mod structure: {} ({}), limit {} per village in group '{}'",
            id, needs, maxPerVillage, entry.limitGroup());
      };
      Main.STRUCTURE_REGISTRY.addReloadCallback(registration);
      if (Main.STRUCTURE_REGISTRY.isInitialized()) {
         registration.run();
      }
   }

   public static void registerTemplatePersistent(
      Identifier templateId,
      String displayName,
      Set<VillageNeedsAnalyzer.VillageNeed> needs,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      Runnable registration = () -> {
         StructureEntry entry = new StructureEntry(
            templateId, displayName, needs, requirements, biomePreferences, clearanceSize, StructureEntry.Source.MOD_REGISTERED
         );
         Main.STRUCTURE_REGISTRY.register(entry);
         LOGGER.info("Registered mod template structure: {} ({})", templateId, needs);
      };
      Main.STRUCTURE_REGISTRY.addReloadCallback(registration);
      if (Main.STRUCTURE_REGISTRY.isInitialized()) {
         registration.run();
      }
   }

   public static void registerTemplatePersistent(
      Identifier templateId,
      String displayName,
      VillageNeedsAnalyzer.VillageNeed need,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize
   ) {
      registerTemplatePersistent(templateId, displayName, Set.of(need), requirements, biomePreferences, clearanceSize);
   }

   public static Component getConstructionStatus(ServerLevel world, BlockPos villagePos) {
      VillageData villageData = Main.VILLAGE_DATA_MANAGER.getExistingVillageData(world, villagePos);
      if (villageData != null && villageData.getCurrentPlan() != null) {
         StructurePlan plan = villageData.getCurrentPlan();
         return Component.translatable("message.village-builder.next_project", new Object[]{plan.getDisplayName()});
      } else {
         return null;
      }
   }

   @FunctionalInterface
   public interface ConstructionListener {
      void onConstruction(ServerLevel var1, BlockPos var2, String var3, BlockPos var4);
   }

   public record DonationResult(List<ItemStack> accepted, List<ItemStack> rejected, int overflowLost) {
   }

   @FunctionalInterface
   public interface PlanChangedListener {
      void onPlanChanged(ServerLevel var1, BlockPos var2, String var3);
   }

   /** Reports how many of a limit group a village already contains. */
   @FunctionalInterface
   public interface LimitGroupSeeder {
      int countExisting(ServerLevel world, BlockPos villageCenter);
   }
}
