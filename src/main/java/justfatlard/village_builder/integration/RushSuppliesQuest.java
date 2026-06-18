package justfatlard.village_builder.integration;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.village_builder.Main;
import justfatlard.village_quests.quest.VillagerQuest;
import justfatlard.village_quests.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public class RushSuppliesQuest extends VillagerQuest {
   private static final long TIME_LIMIT_TICKS = 6000L; // 5 minutes

   private final Item requiredItem;
   private final int requiredAmount;
   private final BlockPos tablePos;

   public RushSuppliesQuest(String requesterName, UUID villagerUuid, Item item, int amount, BlockPos tablePos) {
      super(QuestType.TIME_SENSITIVE, requesterName, villagerUuid, 20);
      this.requiredItem = item;
      this.requiredAmount = amount;
      this.tablePos = tablePos;
   }

   @Override
   public String getDescription() {
      String itemName = requiredItem.getName(requiredItem.getDefaultInstance()).getString().toLowerCase();
      String[] asks = {
         "Construction stops today without " + requiredAmount + " " + itemName + ". I need it now — not tomorrow.",
         "We're out of " + itemName + " and the crew is standing idle. " + requiredAmount + " pieces, quickly.",
         "I'm not in the habit of rushing people. But I need " + requiredAmount + " " + itemName + " before the light goes.",
      };
      return requesterName + ": \"" + asks[ThreadLocalRandom.current().nextInt(asks.length)] + "\"";
   }

   @Override
   public String getObjective() {
      String itemName = requiredItem.getName(requiredItem.getDefaultInstance()).getString().toLowerCase();
      return "Quickly deliver " + requiredAmount + " " + itemName + " — time is running out";
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
      if (player.level() instanceof ServerLevel world) {
         long currentTick = world.getServer().getTickCount();
         this.initStartTick(currentTick);
         if (this.startTick > 0 && currentTick - this.startTick > TIME_LIMIT_TICKS) {
            this.gracefulFailure = true;
            return false;
         }
      }
      return InventoryHelper.countItem(player.getInventory(), requiredItem) >= requiredAmount;
   }

   @Override
   public void onComplete(ServerPlayer player) {
      InventoryHelper.removeItem(player.getInventory(), requiredItem, requiredAmount);
      if (player.level() instanceof ServerLevel world) {
         Main.VILLAGE_DATA_MANAGER.addMaterialToVillage(world, tablePos, requiredItem, requiredAmount);
      }
      String[] responses = {
         "That's it. Thank you. The crew gets back to work now.",
         "I thought I'd have to push the schedule. You saved it.",
         "Put it down — get paid. That's the kind of work I respect."
      };
      player.sendSystemMessage(
         Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
            .withStyle(ChatFormatting.GREEN), false);
      this.completed = true;
   }

   @Override
   public String getFailureAftermathText() {
      return requesterName + ": \"Too late. Construction's stalled for today. Maybe next time.\"";
   }
}
