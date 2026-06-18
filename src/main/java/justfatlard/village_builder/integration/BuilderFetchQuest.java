package justfatlard.village_builder.integration;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_quests.quest.VillagerQuest;
import justfatlard.village_quests.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public class BuilderFetchQuest extends VillagerQuest {
   private final Item requiredItem;
   private final int requiredAmount;
   private final BlockPos tablePos;

   public BuilderFetchQuest(String requesterName, UUID villagerUuid, Item item, int amount, BlockPos tablePos) {
      super(QuestType.FETCH, requesterName, villagerUuid, 12);
      this.requiredItem = item;
      this.requiredAmount = amount;
      this.tablePos = tablePos;
   }

   @Override
   public String getDescription() {
      String itemName = requiredItem.getName(requiredItem.getDefaultInstance()).getString().toLowerCase();
      String[] asks = {
         "We're short on " + itemName + " for the next building. Can you gather some?",
         "The construction is waiting on " + itemName + ". " + requiredAmount + " pieces would get us moving again.",
         "I've planned the next structure but the " + itemName + " isn't here yet. Can you help?",
         "If you have " + requiredAmount + " " + itemName + " to spare, the village could use them right now."
      };
      return requesterName + ": \"" + asks[ThreadLocalRandom.current().nextInt(asks.length)] + "\"";
   }

   @Override
   public String getObjective() {
      String itemName = requiredItem.getName(requiredItem.getDefaultInstance()).getString().toLowerCase();
      return "Bring " + requiredAmount + " " + itemName + " for village construction";
   }

   @Override
   public Item getSubmissionItem() {
      return requiredItem;
   }

   @Override
   public int getSubmissionAmount() {
      return requiredAmount;
   }

   @Override
   public boolean checkCompletion(ServerPlayer player) {
      return InventoryHelper.countItem(player.getInventory(), requiredItem) >= requiredAmount;
   }

   @Override
   public void onComplete(ServerPlayer player) {
      InventoryHelper.removeItem(player.getInventory(), requiredItem, requiredAmount);
      if (player.level() instanceof ServerLevel world) {
         Main.VILLAGE_DATA_MANAGER.addMaterialToVillage(world, tablePos, requiredItem, requiredAmount);
      }
      String[] responses = {
         "Good. That's exactly what we needed.",
         "*stacks it carefully* This goes straight into the build.",
         "Finally. The crew's been waiting on this.",
         "Set it by the table. I'll make sure it gets used right.",
         "That's one less thing keeping us from breaking ground."
      };
      player.sendSystemMessage(
         Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
            .withStyle(ChatFormatting.GREEN), false);
      this.completed = true;
   }
}
