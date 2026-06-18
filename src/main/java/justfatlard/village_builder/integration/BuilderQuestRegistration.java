package justfatlard.village_builder.integration;

import java.util.List;
import java.util.Random;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.util.BuildersTableFinder;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_quests.api.QuestRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuilderQuestRegistration {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");

   public static void register() {
      // professionName is the path of the Identifier — "village-builder:builder" → "builder"
      QuestRegistry.registerProfessionQuest("builder", (villager, villagerName, reputation, random) -> {
         if (!(villager.level() instanceof ServerLevel world)) return null;

         BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(world, villager.blockPosition());
         if (tablePos == null) return null;

         VillageData data = Main.VILLAGE_DATA_MANAGER.getExistingVillageData(world, tablePos);
         if (data == null) return null;

         StructurePlan plan = data.getCurrentPlan();
         if (plan == null) return null;

         List<StructureType.MaterialRequirement> needed = plan.getRequirements().stream()
            .filter(req -> data.getMaterialCount(req.item()) < req.amount())
            .toList();

         if (needed.isEmpty()) {
            // Materials are gathered — survey quest gives the player a meaningful thing to do
            if (reputation >= 25) {
               BlockPos surveyTarget = pickSurveyTarget(world, tablePos, random);
               if (surveyTarget != null) {
                  return new SurveyBuildSiteQuest(villagerName, villager.getUUID(), surveyTarget);
               }
            }
            return null;
         }

         StructureType.MaterialRequirement target = pickWeightedRequirement(needed, data, random);
         int have = data.getMaterialCount(target.item());
         int stillNeeded = target.amount() - have;
         int questAmount = Math.max(1, Math.min(stillNeeded, Math.min(32, target.amount() / 4 + 1)));

         // Near completion + trust + luck → urgent variant
         float completion = data.getCompletionPercentage();
         if (completion >= 0.6f && reputation >= 25 && random.nextFloat() < 0.35f) {
            int rushAmount = Math.max(1, Math.min(stillNeeded, questAmount / 2 + 1));
            return new RushSuppliesQuest(villagerName, villager.getUUID(), target.item(), rushAmount, tablePos);
         }

         return new BuilderFetchQuest(villagerName, villager.getUUID(), target.item(), questAmount, tablePos);
      });

      LOGGER.info("Registered builder quest types with village-quests (BuilderFetch, SurveyBuildSite, RushSupplies)");
   }

   private static StructureType.MaterialRequirement pickWeightedRequirement(
      List<StructureType.MaterialRequirement> needed, VillageData data, Random random
   ) {
      // Items furthest from completion get higher weight
      double totalWeight = 0;
      double[] weights = new double[needed.size()];
      for (int i = 0; i < needed.size(); i++) {
         StructureType.MaterialRequirement req = needed.get(i);
         double shortage = 1.0 - (double) data.getMaterialCount(req.item()) / req.amount();
         weights[i] = shortage;
         totalWeight += shortage;
      }
      if (totalWeight <= 0) return needed.get(random.nextInt(needed.size()));
      double roll = random.nextDouble() * totalWeight;
      double cumulative = 0;
      for (int i = 0; i < needed.size(); i++) {
         cumulative += weights[i];
         if (roll <= cumulative) return needed.get(i);
      }
      return needed.get(needed.size() - 1);
   }

   private static BlockPos pickSurveyTarget(ServerLevel world, BlockPos tablePos, Random random) {
      for (int attempt = 0; attempt < 10; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         int radius = 16 + random.nextInt(24);
         int x = tablePos.getX() + (int) (Math.cos(angle) * radius);
         int z = tablePos.getZ() + (int) (Math.sin(angle) * radius);
         if (world.hasChunkAt(x >> 4, z >> 4)) {
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (world.getBlockState(candidate.below()).isSolid() && world.getBlockState(candidate).isAir()) {
               return candidate;
            }
         }
      }
      return null;
   }
}
