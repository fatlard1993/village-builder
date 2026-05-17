package justfatlard.village_builder.api;

import java.util.ArrayList;
import java.util.List;
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
      VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageData(world, donationPos);
      if (villageData == null) {
         rejected.addAll(donatedItems);
         return new VillageBuilderAPI.DonationResult(accepted, rejected, 0);
      } else {
         for (ItemStack donated : donatedItems) {
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
}
