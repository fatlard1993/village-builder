package justfatlard.village_builder.integration;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.village_builder.Main;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class SurveyBuildSiteQuest extends VillagerQuest {
   private static final double COMPLETION_RADIUS_SQ = 25.0; // 5 blocks

   private final BlockPos surveyTarget;

   public SurveyBuildSiteQuest(String requesterName, UUID villagerUuid, BlockPos surveyTarget) {
      super(QuestType.VILLAGE_DEVELOPMENT, requesterName, villagerUuid, 10);
      this.surveyTarget = surveyTarget;
   }

   @Override
   public String getDescription() {
      String loc = surveyTarget.toShortString();
      String[] descriptions = {
         requesterName + ": \"I've got my eye on a spot near " + loc + " for the next building. Can you check if the ground's solid and there's room?\"",
         requesterName + ": \"There's a clearing around " + loc + " that might work. Walk it for me — tell me if it's right.\"",
         requesterName + ": \"I need someone to look at " + loc + " before I commit to building there. Does it seem stable?\"",
      };
      return descriptions[ThreadLocalRandom.current().nextInt(descriptions.length)];
   }

   @Override
   public String getObjective() {
      return "Survey the potential build site near " + surveyTarget.toShortString();
   }

   @Override
   public boolean checkCompletion(ServerPlayer player) {
      return player.blockPosition().distSqr(surveyTarget) <= COMPLETION_RADIUS_SQ;
   }

   @Override
   public void onComplete(ServerPlayer player) {
      player.getInventory().add(new ItemStack(Main.BUILDERS_FLAG_ITEM, 1));
      String[] responses = {
         "Good ground. Good sight lines. Use the flag I gave you to stake the spot — I'll build there next.",
         "Solid enough. Mark it with that flag if it's where you want the next build. I'll trust your eye.",
         "Sounds promising. Plant the flag and I'll know where to break ground.",
         "That works. If you want to claim the site, stake it. I'll see what goes up there."
      };
      player.sendSystemMessage(
         Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
            .withStyle(ChatFormatting.GREEN), true);
      this.completed = true;
   }
}
