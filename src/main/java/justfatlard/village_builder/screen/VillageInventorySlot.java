package justfatlard.village_builder.screen;

import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.integration.VillageQuestsIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class VillageInventorySlot extends Slot {
   private static final int REPUTATION_COST_PER_ITEM = 1;
   private final BlockPos villageCenter;
   private final boolean isVillageSlot;

   public VillageInventorySlot(Container inventory, int index, int x, int y, BlockPos villageCenter, boolean isVillageSlot) {
      super(inventory, index, x, y);
      this.villageCenter = villageCenter;
      this.isVillageSlot = isVillageSlot;
   }

   @Override
   public boolean mayPlace(ItemStack stack) {
      return this.isVillageSlot && VillageBuilderAPI.isBuildingMaterial(stack.getItem());
   }

   @Override
   public void onTake(Player player, ItemStack stack) {
      super.onTake(player, stack);
      if (this.isVillageSlot && VillageQuestsIntegration.AVAILABLE) {
         if (player instanceof ServerPlayer serverPlayer) {
            int cost = stack.getCount() * REPUTATION_COST_PER_ITEM;
            int currentRep = VillageQuestsIntegration.getPlayerReputation(serverPlayer, this.villageCenter);
            boolean success = VillageQuestsIntegration.modifyPlayerReputation(serverPlayer, this.villageCenter, -cost, "Took items from Builder's Table");
            if (!success) {
               serverPlayer.sendSystemMessage(Component.translatable("message.village-builder.reputation_error"));
            } else {
               int newRep = currentRep - cost;
               if (newRep < 0) {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.village-builder.reputation_hostile", cost, newRep));
               } else if (newRep < 10) {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.village-builder.reputation_low", cost, newRep));
               } else {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.village-builder.reputation_deducted", cost, newRep));
               }
            }
         }
      }
   }
}
