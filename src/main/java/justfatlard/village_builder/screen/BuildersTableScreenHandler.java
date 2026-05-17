package justfatlard.village_builder.screen;

import justfatlard.village_builder.Main;
import justfatlard.village_builder.village.VillageData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BuildersTableScreenHandler extends AbstractContainerMenu {
   private final Container inventory;
   private final VillageData villageData;
   private final ServerLevel world;
   private final BlockPos tablePos;
   private final BuildersTableData tableData;

   // Server-side constructor
   public BuildersTableScreenHandler(int syncId, Inventory playerInventory, ServerLevel world, BlockPos tablePos) {
      super(Main.BUILDERS_TABLE_SCREEN_HANDLER, syncId);
      this.world = world;
      this.tablePos = tablePos;
      this.villageData = Main.VILLAGE_DATA_MANAGER.getVillageData(world, tablePos);
      this.inventory = this.villageData != null ? this.villageData.getInventoryForTable(tablePos) : new SimpleContainer(27);
      this.tableData = null;
      this.setupSlots(playerInventory);
   }

   // Client-side constructor
   public BuildersTableScreenHandler(int syncId, Inventory playerInventory, BuildersTableData data) {
      super(Main.BUILDERS_TABLE_SCREEN_HANDLER, syncId);
      this.world = null;
      this.tablePos = BlockPos.ZERO;
      this.villageData = null;
      this.inventory = new SimpleContainer(27);
      this.tableData = data;
      this.setupSlots(playerInventory);
   }

   private void setupSlots(Inventory playerInventory) {
      // Village inventory slots (3 rows of 9)
      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new VillageInventorySlot(this.inventory, col + row * 9, 8 + col * 18, 18 + row * 18, this.tablePos, true));
         }
      }

      // Player inventory (3 rows of 9)
      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
         }
      }

      // Player hotbar
      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 144));
      }
   }

   @Override
   public ItemStack quickMoveStack(Player player, int slotIndex) {
      ItemStack newStack = ItemStack.EMPTY;
      Slot slot = this.slots.get(slotIndex);
      if (slot.hasItem()) {
         ItemStack originalStack = slot.getItem();
         newStack = originalStack.copy();
         if (slotIndex < 27) {
            // Moving from village inventory to player inventory
            if (!this.moveItemStackTo(originalStack, 27, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else {
            // Moving from player inventory to village inventory
            int countBefore = originalStack.getCount();
            if (!this.moveItemStackTo(originalStack, 0, 27, false)) {
               return ItemStack.EMPTY;
            }

            int moved = countBefore - originalStack.getCount();
            if (moved > 0 && this.world != null && this.villageData != null) {
               float completion = this.villageData.getCompletionPercentage() * 100.0F;
               String itemName = Component.translatable(newStack.getItem().getDescriptionId()).getString();
               player.sendOverlayMessage(
                  Component.translatable("message.village-builder.materials_added", moved, itemName, String.format("%.1f", completion))
               );
               if (player instanceof ServerPlayer serverPlayer) {
                  this.world.playSound(null, serverPlayer.blockPosition(),
                     SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                     0.2F, 1.0F + this.world.getRandom().nextFloat() * 0.4F);
               }
            }
         }

         if (originalStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }
      }
      return newStack;
   }

   @Override
   public boolean stillValid(Player player) {
      return player.distanceToSqr(this.tablePos.getX() + 0.5, this.tablePos.getY() + 0.5, this.tablePos.getZ() + 0.5) <= 64.0;
   }

   @Override
   public void removed(Player player) {
      super.removed(player);
      if (this.villageData != null) {
         this.villageData.markDirty();
         Main.VILLAGE_DATA_MANAGER.markPersistentDirty();
      }
   }

   public VillageData getVillageData() {
      return this.villageData;
   }

   public BuildersTableData getTableData() {
      return this.tableData;
   }
}
